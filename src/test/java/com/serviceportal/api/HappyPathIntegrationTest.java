package com.serviceportal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.RouteView;
import com.serviceportal.model.*;
import com.serviceportal.repository.UserRepository;
import com.serviceportal.service.ProfileService;
import com.serviceportal.service.RoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One technician and one customer go through the whole flow on an in-memory H2 database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixit-test;DB_CLOSE_DELAY=-1",
        "app.admin.email=admin@fixit.local",
        "app.matching.provider=keyword",
        "app.admin.password=Admin@12345"})
@AutoConfigureMockMvc
class HappyPathIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileService profiles;
    @MockBean RoutingService routing;

    private static final RequestPostProcessor ADMIN = user("admin@fixit.local").roles("ADMIN");
    private static final RequestPostProcessor CUSTOMER = user("sam@test.lk").roles("CUSTOMER");
    private static final RequestPostProcessor TECH = user("nimal@test.lk").roles("TECHNICIAN");

    private UserAccount account(String email, String nic, Role role, String gender) {
        UserAccount u = new UserAccount();
        u.setFullName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPassword("x");
        u.setContactNumber("0771234567");
        u.setGender(gender);
        u.setNic(nic);
        u.setAddress("Colombo");
        u.setRole(role);
        u = users.save(u);
        if (role == Role.TECHNICIAN) profiles.technicianProfile(u);
        else profiles.preference(u);
        return u;
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder rb, int expected) throws Exception {
        var res = mvc.perform(rb).andExpect(status().is(expected)).andReturn().getResponse();
        String body = res.getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonBody(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    @Test
    void fullFlowFromRegistrationToPaid() throws Exception {
        UserAccount customer = account("sam@test.lk", "900000001V", Role.CUSTOMER, "Male");
        UserAccount tech = account("nimal@test.lk", "900000002V", Role.TECHNICIAN, "Male");

        // reference data seeded
        JsonNode professions = call(get("/api/professions").with(CUSTOMER), 200);
        long electricianId = 0;
        for (JsonNode p : professions) if (p.get("name").asText().equals("Electrician")) electricianId = p.get("id").asLong();
        assertTrue(electricianId > 0);
        JsonNode langs = call(get("/api/languages").with(CUSTOMER), 200);
        assertEquals(3, langs.size());
        long languageId = langs.get(0).get("id").asLong();

        // 1. unverified users are blocked
        call(jsonBody(post("/api/bookings").with(CUSTOMER), Map.of(
                "technicianId", tech.getId(), "professionId", electricianId, "description", "x",
                "jobLatitude", 6.93, "jobLongitude", 79.85, "scheduledAt", LocalDateTime.now().plusDays(1).toString(),
                "proposedPrice", 5000)), 403);
        call(get("/api/discovery/technicians?lat=6.9&lng=79.8").with(CUSTOMER), 403);
        // only admins can verify
        call(post("/api/admin/users/" + tech.getId() + "/approve").with(CUSTOMER), 403);
        call(get("/api/me"), 401);

        // 2. technician fills the profile
        call(jsonBody(put("/api/me/location").with(TECH), Map.of("latitude", 6.9271, "longitude", 79.8612, "address", "Fort, Colombo")), 200);
        call(jsonBody(put("/api/me/languages").with(TECH), Map.of("languageIds", List.of(languageId))), 200);
        call(jsonBody(put("/api/technician/profile").with(TECH), Map.of("qualification", "NVQ Level 4", "experienceYears", 8)), 200);
        JsonNode withSkill = call(jsonBody(post("/api/technician/skills").with(TECH), Map.of(
                "professionId", electricianId, "skillLevel", "EXPERT", "notes", "Wiring and repairs")), 200);
        assertEquals(1, withSkill.get("skills").size());

        // 3. customer sets location and preferences
        call(jsonBody(put("/api/me/location").with(CUSTOMER), Map.of("latitude", 6.9344, "longitude", 79.8428)), 200);
        call(jsonBody(put("/api/me/languages").with(CUSTOMER), Map.of("languageIds", List.of(languageId))), 200);
        call(jsonBody(put("/api/customer/preferences").with(CUSTOMER), Map.of(
                "preferredGender", "Male", "maxDistanceKm", 10, "budgetMin", 1000, "budgetMax", 8000,
                "minRating", 3.5, "preferredLanguageIds", List.of(languageId))), 200);

        // 4. admin queue: both are pending, then verified
        JsonNode pending = call(get("/api/admin/users?status=PENDING").with(ADMIN), 200);
        assertTrue(pending.size() >= 2);
        call(post("/api/admin/users/" + tech.getId() + "/approve").with(ADMIN), 200);
        call(jsonBody(post("/api/admin/users/" + customer.getId() + "/reject").with(ADMIN), Map.of("reason", "NIC photo unclear")), 200);
        assertEquals("REJECTED", call(get("/api/me").with(CUSTOMER), 200).get("status").asText());
        call(get("/api/discovery/technicians").with(CUSTOMER), 403); // rejected is still blocked
        // editing a rejected profile puts it back in the queue
        call(jsonBody(put("/api/me/location").with(CUSTOMER), Map.of("latitude", 6.9344, "longitude", 79.8428)), 200);
        assertEquals("PENDING", call(get("/api/me").with(CUSTOMER), 200).get("status").asText());
        call(post("/api/admin/users/" + customer.getId() + "/approve").with(ADMIN), 200);

        // 5. discovery: nearby with saved preferences, then by problem description
        JsonNode nearby = call(get("/api/discovery/technicians").with(CUSTOMER), 200);
        assertEquals(1, nearby.size());
        assertEquals(tech.getId(), nearby.get(0).get("id").asLong());
        assertTrue(nearby.get(0).get("distanceKm").asDouble() < 5);
        assertFalse(nearby.get(0).get("rated").asBoolean());
        assertEquals(0, call(get("/api/discovery/technicians?gender=Female").with(CUSTOMER), 200).size());
        assertEquals(0, call(get("/api/discovery/technicians?radiusKm=0.1&usePreferences=false").with(CUSTOMER), 200).size());

        JsonNode match = call(jsonBody(post("/api/discovery/match").with(CUSTOMER), Map.of(
                "description", "The wiring is old and a socket sparks when I plug in the fan")), 200);
        assertEquals("Electrician", match.get("best").get("profession").asText());
        assertEquals(1, match.get("technicians").size());

        // 6. booking with price bidding inside the range
        String when = LocalDateTime.now().plusDays(2).withNano(0).toString();
        Map<String, Object> bad = Map.of("technicianId", tech.getId(), "professionId", electricianId,
                "description", "Fix sparking socket", "jobLatitude", 6.95, "jobLongitude", 79.87,
                "jobAddress", "Kotte", "scheduledAt", when, "proposedPrice", 100);
        call(jsonBody(post("/api/bookings").with(CUSTOMER), bad), 400); // below the platform range

        JsonNode booking = call(jsonBody(post("/api/bookings").with(CUSTOMER), Map.of(
                "technicianId", tech.getId(), "professionId", electricianId, "description", "Fix sparking socket",
                "jobLatitude", 6.95, "jobLongitude", 79.87, "jobAddress", "Kotte", "scheduledAt", when,
                "proposedPrice", 4000)), 200);
        long bookingId = booking.get("id").asLong();
        assertEquals("REQUESTED", booking.get("status").asText());
        assertTrue(booking.get("technician").get("contactNumber").isNull(), "contact hidden before the price is agreed");

        call(post("/api/bookings/" + bookingId + "/accept").with(CUSTOMER), 409);          // cannot accept own offer
        call(jsonBody(post("/api/bookings/" + bookingId + "/offers").with(TECH), Map.of("amount", 99999)), 400); // above range
        JsonNode countered = call(jsonBody(post("/api/bookings/" + bookingId + "/offers").with(TECH), Map.of("amount", 5000)), 200);
        assertEquals("NEGOTIATING", countered.get("status").asText());
        assertEquals(2, countered.get("offers").size());
        JsonNode confirmed = call(post("/api/bookings/" + bookingId + "/accept").with(CUSTOMER), 200);
        assertEquals("CONFIRMED", confirmed.get("status").asText());
        assertEquals(5000.0, confirmed.get("agreedPrice").asDouble());
        assertEquals(500.0, confirmed.get("serviceCharge").asDouble());
        assertEquals(4500.0, confirmed.get("technicianPayout").asDouble());
        assertFalse(confirmed.get("customer").get("contactNumber").isNull(), "contact shown once confirmed");

        // 7. technician routes to the job (routing provider mocked)
        when(routing.routes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(
                new RouteView("DRIVING", "Car / taxi", null, 4.2, 14.0, List.of(), List.of(new double[]{6.92, 79.86}), null)));
        JsonNode routes = call(get("/api/technician/bookings/" + bookingId + "/routes").with(TECH), 200);
        assertEquals("DRIVING", routes.get("routes").get(0).get("mode").asText());
        assertEquals(6.95, routes.get("toLatitude").asDouble());
        call(get("/api/technician/bookings/" + bookingId + "/routes").with(CUSTOMER), 403);

        // 8. job status machine
        call(post("/api/bookings/" + bookingId + "/complete").with(TECH), 409);            // not started yet
        call(post("/api/bookings/" + bookingId + "/start").with(TECH), 200);
        call(jsonBody(post("/api/bookings/" + bookingId + "/rating").with(CUSTOMER), Map.of("score", 5)), 409); // not completed
        call(post("/api/bookings/" + bookingId + "/complete").with(TECH), 200);

        // 9. payments: 10% service charge on the platform, balance in cash
        var proof = new MockMultipartFile("file", "slip.png", "image/png", new byte[]{1, 2, 3, 4});
        call(multipart("/api/bookings/" + bookingId + "/payments").file(proof).with(CUSTOMER)
                .param("type", "SERVICE_CHARGE").param("method", "CASH"), 400);            // charge must use the platform
        call(multipart("/api/bookings/" + bookingId + "/payments").with(CUSTOMER)
                .param("type", "SERVICE_CHARGE").param("method", "PLATFORM"), 400);        // proof required
        var badType = new MockMultipartFile("file", "slip.exe", "application/octet-stream", new byte[]{1});
        call(multipart("/api/bookings/" + bookingId + "/payments").file(badType).with(CUSTOMER)
                .param("type", "SERVICE_CHARGE").param("method", "PLATFORM"), 400);        // wrong file type

        JsonNode charge = call(multipart("/api/bookings/" + bookingId + "/payments").file(proof).with(CUSTOMER)
                .param("type", "SERVICE_CHARGE").param("method", "PLATFORM"), 200);
        assertEquals(500.0, charge.get("amount").asDouble());
        assertEquals("PENDING", charge.get("status").asText());
        JsonNode cash = call(multipart("/api/bookings/" + bookingId + "/payments").with(CUSTOMER)
                .param("type", "BALANCE").param("method", "CASH"), 200);
        assertEquals(4500.0, cash.get("amount").asDouble());

        call(post("/api/payments/" + charge.get("id").asLong() + "/confirm").with(TECH), 403); // only admin confirms platform payments
        call(post("/api/payments/" + cash.get("id").asLong() + "/confirm").with(ADMIN), 403);  // only technician confirms cash
        assertEquals(1, call(get("/api/admin/payments/pending").with(ADMIN), 200).size());

        call(post("/api/payments/" + charge.get("id").asLong() + "/confirm").with(ADMIN), 200);
        assertEquals("COMPLETED", call(get("/api/bookings/" + bookingId).with(CUSTOMER), 200).get("status").asText());
        call(post("/api/payments/" + cash.get("id").asLong() + "/confirm").with(TECH), 200);
        assertEquals("PAID", call(get("/api/bookings/" + bookingId).with(CUSTOMER), 200).get("status").asText());

        // saved and retrievable
        assertEquals(2, call(get("/api/bookings/" + bookingId + "/payments").with(CUSTOMER), 200).size());
        assertEquals(2, call(get("/api/payments").with(TECH), 200).size());
        assertEquals(2, call(get("/api/payments?status=CONFIRMED").with(CUSTOMER), 200).size());
        assertEquals(0, call(get("/api/payments?from=2999-01-01T00:00:00").with(CUSTOMER), 200).size());
        byte[] downloaded = mvc.perform(get("/api/payments/" + charge.get("id").asLong() + "/proof").with(TECH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{1, 2, 3, 4}, downloaded);

        // 10. rating feeds the technician profile, then the dashboard
        call(jsonBody(post("/api/bookings/" + bookingId + "/rating").with(CUSTOMER), Map.of("score", 5, "comment", "Quick and tidy")), 200);
        call(jsonBody(post("/api/bookings/" + bookingId + "/rating").with(CUSTOMER), Map.of("score", 4)), 409); // once per booking
        JsonNode dash = call(get("/api/technician/dashboard").with(TECH), 200);
        assertEquals(4500.0, dash.get("totalEarned").asDouble());
        assertEquals(500.0, dash.get("serviceChargePaid").asDouble());
        assertEquals(5.0, dash.get("ratingAverage").asDouble());
        assertEquals(1, dash.get("customers").size());

        // the rating is now visible in discovery
        JsonNode again = call(get("/api/discovery/technicians").with(CUSTOMER), 200);
        assertTrue(again.get(0).get("rated").asBoolean());
        assertEquals(5.0, again.get(0).get("ratingAverage").asDouble());
    }
}

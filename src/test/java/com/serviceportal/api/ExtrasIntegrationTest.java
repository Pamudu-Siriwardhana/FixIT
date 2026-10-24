package com.serviceportal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.PlaceView;
import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import com.serviceportal.service.GeocodingService;
import com.serviceportal.service.RoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Document upload, detail editing, public profiles, address search and payout records. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixit-extras-test;DB_CLOSE_DELAY=-1",
        "app.admin.email=admin@fixit.local",
        "app.matching.provider=keyword",
        "app.admin.password=Admin@12345"})
@AutoConfigureMockMvc
class ExtrasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfessionRepository professions;
    @Autowired BookingRepository bookings;
    @Autowired PaymentRepository payments;
    @MockBean RoutingService routing;
    @MockBean GeocodingService geocoding;

    private static final RequestPostProcessor ADMIN = user("admin@fixit.local").roles("ADMIN");
    private static RequestPostProcessor as(String email, String role) {
        return user(email).roles(role);
    }

    private UserAccount account(String email, String nic, Role role) {
        UserAccount u = new UserAccount();
        u.setFullName("Kamal Perera");
        u.setEmail(email);
        u.setPassword("x");
        u.setContactNumber("0771234567");
        u.setGender("Male");
        u.setNic(nic);
        u.setAddress("Colombo");
        u.setRole(role);
        return users.save(u);
    }

    private JsonNode call(RequestBuilder rb, int expected) throws Exception {
        String body = mvc.perform(rb).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonBody(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    @Test
    void editingBasicDetails() throws Exception {
        UserAccount c = account("d-cust@test.lk", "910000001V", Role.CUSTOMER);
        JsonNode updated = call(jsonBody(put("/api/me").with(as("d-cust@test.lk", "CUSTOMER")), Map.of(
                "fullName", "Kamal Silva", "contactNumber", "+94771112233", "gender", "Other")), 200);
        assertEquals("Kamal Silva", updated.get("fullName").asText());
        assertEquals("+94771112233", updated.get("contactNumber").asText());
        assertEquals("d-cust@test.lk", updated.get("email").asText());
        assertEquals("910000001V", updated.get("nic").asText());
        call(jsonBody(put("/api/me").with(as("d-cust@test.lk", "CUSTOMER")), Map.of("fullName", "Kamal", "contactNumber", "abc", "gender", "Male")), 400);
        call(jsonBody(put("/api/me").with(as("d-cust@test.lk", "CUSTOMER")), Map.of("fullName", " ", "contactNumber", "0771234567", "gender", "Male")), 400);
        call(jsonBody(put("/api/me"), Map.of("fullName", "X", "contactNumber", "0771234567", "gender", "Male")), 401);
        assertNotNull(c.getId());
    }

    @Test
    void verificationDocuments() throws Exception {
        account("doc-cust@test.lk", "910000002V", Role.CUSTOMER);
        UserAccount custNow = users.findByEmail("doc-cust@test.lk").orElseThrow();
        account("doc-other@test.lk", "910000003V", Role.CUSTOMER);

        var pdf = new MockMultipartFile("file", "nic front.pdf", "application/pdf", new byte[]{9, 8, 7});
        JsonNode doc = call(multipart("/api/me/documents").file(pdf).param("label", "NIC front").with(as("doc-cust@test.lk", "CUSTOMER")), 200);
        long id = doc.get("id").asLong();
        assertEquals("NIC front", doc.get("label").asText());
        assertEquals(3, doc.get("size").asLong());
        assertEquals(1, call(get("/api/me/documents").with(as("doc-cust@test.lk", "CUSTOMER")), 200).size());

        call(multipart("/api/me/documents").file(new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1})).with(as("doc-cust@test.lk", "CUSTOMER")), 400);
        call(multipart("/api/me/documents").file(new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])).with(as("doc-cust@test.lk", "CUSTOMER")), 413);
        call(multipart("/api/me/documents").file(pdf).param("label", "NIC front"), 401);

        byte[] bytes = mvc.perform(get("/api/documents/" + id + "/file").with(as("doc-cust@test.lk", "CUSTOMER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{9, 8, 7}, bytes);
        call(get("/api/documents/" + id + "/file").with(as("doc-other@test.lk", "CUSTOMER")), 403);
        call(get("/api/documents/" + id + "/file").with(ADMIN), 200);
        assertEquals(1, call(get("/api/admin/users/" + custNow.getId() + "/documents").with(ADMIN), 200).size());
        call(get("/api/admin/users/" + custNow.getId() + "/documents").with(as("doc-cust@test.lk", "CUSTOMER")), 403);

        call(delete("/api/me/documents/" + id).with(as("doc-other@test.lk", "CUSTOMER")), 403);
        // a rejected profile that uploads new evidence goes back into the queue
        call(jsonBody(post("/api/admin/users/" + custNow.getId() + "/reject").with(ADMIN), Map.of("reason", "blurry")), 200);
        call(multipart("/api/me/documents").file(pdf).param("label", "NIC back").with(as("doc-cust@test.lk", "CUSTOMER")), 200);
        assertEquals("PENDING", call(get("/api/me").with(as("doc-cust@test.lk", "CUSTOMER")), 200).get("status").asText());

        call(post("/api/admin/users/" + custNow.getId() + "/approve").with(ADMIN), 200);
        call(delete("/api/me/documents/" + id).with(as("doc-cust@test.lk", "CUSTOMER")), 409);   // verified: documents stay
    }

    @Test
    void publicTechnicianProfile() throws Exception {
        UserAccount cust = account("pub-cust@test.lk", "910000004V", Role.CUSTOMER);
        UserAccount tech = account("pub-tech@test.lk", "910000005V", Role.TECHNICIAN);

        call(get("/api/technicians/" + tech.getId()).with(as("pub-cust@test.lk", "CUSTOMER")), 404);        // not verified yet
        call(post("/api/admin/users/" + tech.getId() + "/approve").with(ADMIN), 200);
        JsonNode p = call(get("/api/technicians/" + tech.getId()).with(as("pub-cust@test.lk", "CUSTOMER")), 200);
        assertEquals(tech.getId(), p.get("id").asLong());
        assertEquals(0, p.get("ratingCount").asInt());
        assertEquals(0, p.get("reviews").size());
        assertFalse(p.has("contactNumber"));
        assertFalse(p.has("nic"));
        assertFalse(p.has("email"));
        call(get("/api/technicians/" + cust.getId()).with(as("pub-cust@test.lk", "CUSTOMER")), 404);        // customers have no public profile
        call(get("/api/technicians/" + tech.getId()), 401);
        call(get("/api/technicians/99999").with(as("pub-cust@test.lk", "CUSTOMER")), 404);
    }

    @Test
    void addressSearch() throws Exception {
        account("geo-cust@test.lk", "910000006V", Role.CUSTOMER);
        when(geocoding.search("fort colombo")).thenReturn(List.of(new PlaceView("Fort, Colombo, Sri Lanka", 6.93, 79.84)));
        JsonNode r = call(get("/api/geocode?q=fort colombo").with(as("geo-cust@test.lk", "CUSTOMER")), 200);
        assertEquals(1, r.size());
        assertEquals("Fort, Colombo, Sri Lanka", r.get(0).get("label").asText());
        assertEquals(6.93, r.get(0).get("latitude").asDouble());
        call(get("/api/geocode?q=fort colombo"), 401);
    }

    @Test
    void payoutOfPlatformBalance() throws Exception {
        UserAccount cust = account("pay-cust@test.lk", "910000007V", Role.CUSTOMER);
        UserAccount tech = account("pay-tech@test.lk", "910000008V", Role.TECHNICIAN);
        Profession prof = professions.findAllByOrderByNameAsc().get(0);

        Booking viaPlatform = completedBooking(cust, tech, prof);
        Booking viaCash = completedBooking(cust, tech, prof);
        balance(viaPlatform, cust, PaymentMethod.PLATFORM, PaymentStatus.CONFIRMED);
        balance(viaCash, cust, PaymentMethod.CASH, PaymentStatus.CONFIRMED);

        JsonNode pending = call(get("/api/admin/payouts/pending").with(ADMIN), 200);
        assertEquals(1, pending.size());
        assertEquals(viaPlatform.getId(), pending.get(0).get("bookingId").asLong());
        assertEquals(4500.0, pending.get(0).get("amount").asDouble());
        call(get("/api/admin/payouts/pending").with(as("pay-tech@test.lk", "TECHNICIAN")), 403);

        String url = "/api/admin/bookings/";
        call(jsonBody(post(url + viaPlatform.getId() + "/payout").with(as("pay-tech@test.lk", "TECHNICIAN")), Map.of("reference", "TX1")), 403);
        call(jsonBody(post(url + viaPlatform.getId() + "/payout").with(ADMIN), Map.of("reference", " ")), 400);
        call(jsonBody(post(url + viaCash.getId() + "/payout").with(ADMIN), Map.of("reference", "TX2")), 409); // cash was paid directly
        call(jsonBody(post(url + "99999/payout").with(ADMIN), Map.of("reference", "TX3")), 404);

        JsonNode paid = call(jsonBody(post(url + viaPlatform.getId() + "/payout").with(ADMIN), Map.of("reference", "BANK-123", "note", "Oct")), 200);
        assertEquals(4500.0, paid.get("amount").asDouble());
        assertEquals("BANK-123", paid.get("reference").asText());
        call(jsonBody(post(url + viaPlatform.getId() + "/payout").with(ADMIN), Map.of("reference", "BANK-124")), 409); // once only
        assertEquals(0, call(get("/api/admin/payouts/pending").with(ADMIN), 200).size());
        assertEquals(1, call(get("/api/admin/payouts").with(ADMIN), 200).size());
        assertEquals(1, call(get("/api/technician/payouts").with(as("pay-tech@test.lk", "TECHNICIAN")), 200).size());
        call(get("/api/technician/payouts").with(as("pay-cust@test.lk", "CUSTOMER")), 403);
    }

    private Booking completedBooking(UserAccount cust, UserAccount tech, Profession prof) {
        Booking b = new Booking();
        b.setCustomer(cust);
        b.setTechnician(tech);
        b.setProfession(prof);
        b.setDescription("test job");
        b.setJobLatitude(6.9);
        b.setJobLongitude(79.8);
        b.setStatus(BookingStatus.COMPLETED);
        b.setAgreedPrice(new BigDecimal("5000.00"));
        return bookings.save(b);
    }

    private void balance(Booking b, UserAccount payer, PaymentMethod method, PaymentStatus status) {
        Payment p = new Payment();
        p.setBooking(b);
        p.setPayer(payer);
        p.setType(PaymentType.BALANCE);
        p.setMethod(method);
        p.setAmount(new BigDecimal("4500.00"));
        p.setStatus(status);
        payments.save(p);
    }
}

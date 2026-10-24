package com.serviceportal.api;

import com.serviceportal.model.Role;
import com.serviceportal.model.UserAccount;
import com.serviceportal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders every page through Thymeleaf for each role, so a broken template fails here instead of in the browser. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixit-pages-test;DB_CLOSE_DELAY=-1",
        "app.admin.email=admin@fixit.local",
        "app.matching.provider=keyword",
        "app.admin.password=Admin@12345"})
@AutoConfigureMockMvc
class PagesRenderTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    private static final RequestPostProcessor ADMIN = user("admin@fixit.local").roles("ADMIN");
    private static final RequestPostProcessor CUSTOMER = user("page-customer@test.lk").roles("CUSTOMER");
    private static final RequestPostProcessor TECH = user("page-tech@test.lk").roles("TECHNICIAN");

    private void account(String email, String nic, Role role) {
        if (users.existsByEmail(email)) return;
        UserAccount u = new UserAccount();
        u.setFullName("Page " + role);
        u.setEmail(email);
        u.setPassword("x");
        u.setContactNumber("0771234567");
        u.setGender("Male");
        u.setNic(nic);
        u.setAddress("Colombo");
        u.setRole(role);
        users.save(u);
    }

    private void accounts() {
        account("page-customer@test.lk", "910000001V", Role.CUSTOMER);
        account("page-tech@test.lk", "910000002V", Role.TECHNICIAN);
    }

    @Test
    void publicPagesAndScriptsAreReachableWithoutLogin() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(content().string(containsString("Languages you speak")));
        for (String js : new String[]{"app.js", "map.js", "profile.js", "find.js", "booking.js", "bookings.js", "dashboard.js", "admin.js", "technician-profile.js"}) {
            mvc.perform(get("/js/" + js)).andExpect(status().isOk());
        }
        mvc.perform(get("/css/layout.css")).andExpect(status().isOk());
    }

    @Test
    void protectedPagesRedirectToLogin() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/bookings/1")).andExpect(status().is3xxRedirection());
    }

    @Test
    void customerPages() throws Exception {
        accounts();
        mvc.perform(get("/customer/home").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Find a technician")));
        mvc.perform(get("/customer/find").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Describe the problem")))
                .andExpect(content().string(containsString("leaflet")));
        mvc.perform(get("/profile").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Search preferences")))
                .andExpect(content().string(containsString("data-role=\"CUSTOMER\"")));
        mvc.perform(get("/bookings").with(CUSTOMER)).andExpect(status().isOk());
        mvc.perform(get("/bookings/5").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-id=\"5\"")));
        mvc.perform(get("/technician/dashboard").with(CUSTOMER)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/home").with(CUSTOMER)).andExpect(status().isForbidden());
    }

    @Test
    void technicianPages() throws Exception {
        accounts();
        mvc.perform(get("/technician/home").with(TECH)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")));
        mvc.perform(get("/technician/dashboard").with(TECH)).andExpect(status().isOk());
        mvc.perform(get("/profile").with(TECH)).andExpect(status().isOk())
                .andExpect(content().string(containsString("My professions")))
                .andExpect(content().string(containsString("EXPERT")));
        mvc.perform(get("/customer/find").with(TECH)).andExpect(status().isForbidden());
    }

    @Test
    void adminPage() throws Exception {
        mvc.perform(get("/admin/home").with(ADMIN)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Profiles waiting for verification")))
                .andExpect(content().string(containsString("Professions and price ranges")))
                .andExpect(content().string(containsString("Technician payouts")));
    }

    @Test
    void profileHasDetailsAndDocumentsAndTechnicianPageRenders() throws Exception {
        accounts();
        mvc.perform(get("/profile").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Basic details")))
                .andExpect(content().string(containsString("Verification documents")));
        mvc.perform(get("/technicians/1").with(CUSTOMER)).andExpect(status().isOk())
                .andExpect(content().string(containsString("technician-profile.js")));
        mvc.perform(get("/technicians/1")).andExpect(status().is3xxRedirection());
    }
}

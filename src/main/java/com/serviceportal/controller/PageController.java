package com.serviceportal.controller;

import com.serviceportal.model.SkillLevel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Page shells. The data comes from the REST API (/api/**) through the scripts in /js. */
@Controller
public class PageController {

    /** Profile: map location, languages, customer preferences or technician professions. */
    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("levels", SkillLevel.values());
        return "profile";
    }

    /** Customer: search technicians near a map point, or describe the problem. */
    @GetMapping("/customer/find")
    public String find() {
        return "find";
    }

    @GetMapping("/technician/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    /** Public profile of a verified technician (rating and reviews). */
    @GetMapping("/technicians/{id}")
    public String technician(@PathVariable Long id, Model model) {
        model.addAttribute("technicianId", id);
        return "technician-profile";
    }

    @GetMapping("/bookings")
    public String bookings() {
        return "bookings";
    }

    @GetMapping("/bookings/{id}")
    public String booking(@PathVariable Long id, Model model) {
        model.addAttribute("bookingId", id);
        return "booking";
    }
}

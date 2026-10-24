package com.serviceportal.controller;

import com.serviceportal.model.VerificationStatus;
import com.serviceportal.service.AdminService;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** Home pages and the admin verification screen. */
@Controller
public class HomeController {

    private final CurrentUser currentUser;
    private final AdminService admin;
    private final PaymentService payments;

    public HomeController(CurrentUser currentUser, AdminService admin, PaymentService payments) {
        this.currentUser = currentUser;
        this.admin = admin;
        this.payments = payments;
    }

    @GetMapping("/customer/home")
    public String customerHome(Principal principal, Model model) {
        model.addAttribute("user", currentUser.of(principal));
        return "customer-home";
    }

    @GetMapping("/technician/home")
    public String technicianHome(Principal principal, Model model) {
        model.addAttribute("user", currentUser.of(principal));
        return "technician-home";
    }

    @GetMapping("/admin/home")
    public String adminHome(Principal principal, Model model) {
        model.addAttribute("user", currentUser.of(principal));
        model.addAttribute("pending", admin.usersByStatus(VerificationStatus.PENDING));
        model.addAttribute("payments", payments.pending());
        return "admin-home";
    }

    @PostMapping("/admin/users/{id}/approve")
    public String approve(@PathVariable Long id) {
        admin.approve(id);
        return "redirect:/admin/home";
    }

    @PostMapping("/admin/users/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String reason) {
        admin.reject(id, reason.isBlank() ? "Not specified" : reason.trim());
        return "redirect:/admin/home";
    }

    @PostMapping("/admin/payments/{id}/confirm")
    public String confirmPayment(@PathVariable Long id, Principal principal) {
        payments.review(id, currentUser.of(principal), true, null);
        return "redirect:/admin/home";
    }

    @PostMapping("/admin/payments/{id}/reject")
    public String rejectPayment(@PathVariable Long id, @RequestParam(defaultValue = "Rejected by admin") String reason, Principal principal) {
        payments.review(id, currentUser.of(principal), false, reason);
        return "redirect:/admin/home";
    }
}

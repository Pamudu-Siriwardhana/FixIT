package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.VerificationStatus;
import com.serviceportal.service.AdminService;
import com.serviceportal.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService admin;
    private final PaymentService payments;

    public AdminApiController(AdminService admin, PaymentService payments) {
        this.admin = admin;
        this.payments = payments;
    }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam(defaultValue = "PENDING") VerificationStatus status) {
        return admin.usersByStatus(status);
    }

    @PostMapping("/users/{id}/approve")
    public UserView approve(@PathVariable Long id) {
        return admin.approve(id);
    }

    @PostMapping("/users/{id}/reject")
    public UserView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest r) {
        return admin.reject(id, r.reason());
    }

    @PostMapping("/languages")
    public LanguageView addLanguage(@Valid @RequestBody NameRequest r) {
        return admin.addLanguage(r.name());
    }

    @PostMapping("/professions")
    public ProfessionView createProfession(@Valid @RequestBody ProfessionRequest r) {
        return admin.saveProfession(null, r);
    }

    @PutMapping("/professions/{id}")
    public ProfessionView updateProfession(@PathVariable Long id, @Valid @RequestBody ProfessionRequest r) {
        return admin.saveProfession(id, r);
    }

    @GetMapping("/payments/pending")
    public List<PaymentView> pendingPayments() {
        return payments.pending();
    }
}

package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.Role;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Payouts of platform-collected balances to technicians (record only). */
@RestController
@RequestMapping("/api")
public class PayoutApiController {

    private final CurrentUser currentUser;
    private final PayoutService payouts;

    public PayoutApiController(CurrentUser currentUser, PayoutService payouts) {
        this.currentUser = currentUser;
        this.payouts = payouts;
    }

    @GetMapping("/admin/payouts/pending")
    public List<PendingPayoutView> pending() {
        return payouts.pending();
    }

    @GetMapping("/admin/payouts")
    public List<PayoutView> all(Principal principal) {
        return payouts.history(currentUser.require(principal, Role.ADMIN));
    }

    @PostMapping("/admin/bookings/{bookingId}/payout")
    public PayoutView record(@PathVariable Long bookingId, @Valid @RequestBody PayoutRequest r, Principal principal) {
        return payouts.record(bookingId, currentUser.require(principal, Role.ADMIN), r.reference(), r.note());
    }

    @GetMapping("/technician/payouts")
    public List<PayoutView> mine(Principal principal) {
        return payouts.history(currentUser.require(principal, Role.TECHNICIAN));
    }
}

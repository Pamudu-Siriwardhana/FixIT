package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.Payment;
import com.serviceportal.model.PaymentStatus;
import com.serviceportal.service.ApiException;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private final CurrentUser currentUser;
    private final PaymentService payments;

    public PaymentApiController(CurrentUser currentUser, PaymentService payments) {
        this.currentUser = currentUser;
        this.payments = payments;
    }

    /** Saved payments for the signed-in user, optionally limited by date range (ISO date-time) and status. */
    @GetMapping
    public List<PaymentView> history(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                     @RequestParam(required = false) PaymentStatus status,
                                     Principal principal) {
        return payments.history(currentUser.of(principal), from, to, status);
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<byte[]> proof(@PathVariable Long id, Principal principal) {
        Payment p = payments.forViewer(id, currentUser.of(principal));
        if (!p.hasProof()) throw ApiException.notFound("This payment has no proof file");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getProofContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(p.getProofFileName()).build().toString())
                .body(p.getProofData());
    }

    /** Technician confirms a CASH balance received; admin confirms PLATFORM payments. */
    @PostMapping("/{id}/confirm")
    public PaymentView confirm(@PathVariable Long id, Principal principal) {
        return payments.review(id, currentUser.of(principal), true, null);
    }

    @PostMapping("/{id}/reject")
    public PaymentView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest r, Principal principal) {
        return payments.review(id, currentUser.of(principal), false, r.reason());
    }
}

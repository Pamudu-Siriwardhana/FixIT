package com.serviceportal.service;

import com.serviceportal.dto.Dtos.PaymentView;
import com.serviceportal.model.*;
import com.serviceportal.repository.PaymentRepository;
import com.serviceportal.util.Pricing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Payments are records only (no gateway). Split of the agreed price:
 *  - SERVICE_CHARGE (10%): always PLATFORM, customer uploads proof, admin confirms.
 *  - BALANCE (90%, the technician's payout): CASH (technician confirms receipt) or PLATFORM (proof, admin confirms).
 * When both are confirmed the booking becomes PAID.
 */
@Service
@Transactional
public class PaymentService {

    static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    static final Set<String> PROOF_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    private final PaymentRepository payments;
    private final BookingService bookings;
    private final Views views;

    public PaymentService(PaymentRepository payments, BookingService bookings, Views views) {
        this.payments = payments;
        this.bookings = bookings;
        this.views = views;
    }

    public PaymentView submit(Booking b, UserAccount customer, PaymentType type, PaymentMethod method,
                              String note, MultipartFile proof) {
        if (!b.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.forbidden("Only the customer of this booking can submit payments");
        }
        if (b.getStatus() != BookingStatus.COMPLETED) {
            throw ApiException.conflict("Payments can be submitted once the job is COMPLETED (status is " + b.getStatus() + ")");
        }
        if (type == PaymentType.SERVICE_CHARGE && method != PaymentMethod.PLATFORM) {
            throw ApiException.badRequest("The service charge must be paid through the platform");
        }
        boolean exists = payments.findByBookingOrderByCreatedAtAscIdAsc(b).stream()
                .anyMatch(p -> p.getType() == type && p.getStatus() != PaymentStatus.REJECTED);
        if (exists) throw ApiException.conflict("A " + type + " payment already exists for this booking");

        boolean proofGiven = proof != null && !proof.isEmpty();
        if (method == PaymentMethod.PLATFORM && !proofGiven) {
            throw ApiException.badRequest("Upload proof of payment (PNG, JPEG or PDF)");
        }

        BigDecimal charge = Pricing.serviceCharge(b.getAgreedPrice(), views.chargePercent());
        BigDecimal amount = type == PaymentType.SERVICE_CHARGE ? charge : b.getAgreedPrice().subtract(charge);

        Payment p = new Payment();
        p.setBooking(b);
        p.setPayer(customer);
        p.setType(type);
        p.setMethod(method);
        p.setAmount(amount);
        p.setNote(note);
        if (proofGiven) attachProof(p, proof);
        return views.payment(payments.save(p));
    }

    private void attachProof(Payment p, MultipartFile file) {
        if (file.getSize() > MAX_PROOF_BYTES) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "File too large (max 5 MB)");
        String ct = file.getContentType();
        if (ct == null || !PROOF_TYPES.contains(ct.toLowerCase())) {
            throw ApiException.badRequest("Proof must be a PNG, JPEG or PDF file");
        }
        try {
            p.setProofData(file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        p.setProofContentType(ct.toLowerCase());
        String name = file.getOriginalFilename() == null ? "proof" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        p.setProofFileName(name);
    }

    /** Admin confirms/rejects PLATFORM payments; the technician confirms CASH balance received. */
    public PaymentView review(Long paymentId, UserAccount reviewer, boolean approve, String reason) {
        Payment p = find(paymentId);
        if (p.getStatus() != PaymentStatus.PENDING) throw ApiException.conflict("Payment is already " + p.getStatus());
        boolean cash = p.getMethod() == PaymentMethod.CASH;
        if (cash) {
            if (reviewer.getRole() != Role.TECHNICIAN || !p.getBooking().getTechnician().getId().equals(reviewer.getId())) {
                throw ApiException.forbidden("Only the technician of this booking can confirm a cash payment");
            }
        } else if (reviewer.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only an admin can confirm a platform payment");
        }
        p.setStatus(approve ? PaymentStatus.CONFIRMED : PaymentStatus.REJECTED);
        p.setReviewedBy(reviewer);
        p.setReviewedAt(LocalDateTime.now());
        if (!approve && reason != null) p.setNote(reason);
        payments.save(p);
        if (approve) settle(p.getBooking());
        return views.payment(p);
    }

    private void settle(Booking b) {
        List<Payment> all = payments.findByBookingOrderByCreatedAtAscIdAsc(b);
        boolean charge = all.stream().anyMatch(x -> x.getType() == PaymentType.SERVICE_CHARGE && x.getStatus() == PaymentStatus.CONFIRMED);
        boolean balance = all.stream().anyMatch(x -> x.getType() == PaymentType.BALANCE && x.getStatus() == PaymentStatus.CONFIRMED);
        if (charge && balance && b.getStatus() == BookingStatus.COMPLETED) {
            bookings.markPaid(b);
        }
    }

    public Payment find(Long id) {
        return payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment not found"));
    }

    /** Payment visible to the payer, the booking's technician or an admin. */
    public Payment forViewer(Long id, UserAccount user) {
        Payment p = find(id);
        boolean ok = user.getRole() == Role.ADMIN || p.getPayer().getId().equals(user.getId())
                || p.getBooking().getTechnician().getId().equals(user.getId());
        if (!ok) throw ApiException.forbidden("Not your payment");
        return p;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> forBooking(Booking b) {
        return payments.findByBookingOrderByCreatedAtAscIdAsc(b).stream().map(views::payment).toList();
    }

    /** History for the user (customer: payments made, technician: payments on their jobs, admin: all). */
    @Transactional(readOnly = true)
    public List<PaymentView> history(UserAccount user, LocalDateTime from, LocalDateTime to, PaymentStatus status) {
        LocalDateTime f = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from;
        LocalDateTime t = to == null ? LocalDateTime.of(2999, 1, 1, 0, 0) : to;
        List<Payment> list = user.getRole() == Role.ADMIN ? payments.findAllBetween(f, t) : payments.findForUser(user, f, t);
        return list.stream().filter(p -> status == null || p.getStatus() == status).map(views::payment).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> pending() {
        return payments.findByStatusOrderByCreatedAtAsc(PaymentStatus.PENDING).stream()
                .filter(p -> p.getMethod() == PaymentMethod.PLATFORM).map(views::payment).toList();
    }
}

package com.serviceportal.service;

import com.serviceportal.dto.Dtos.PayoutView;
import com.serviceportal.dto.Dtos.PendingPayoutView;
import com.serviceportal.model.*;
import com.serviceportal.repository.BookingRepository;
import com.serviceportal.repository.PaymentRepository;
import com.serviceportal.repository.PayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * When a customer pays the balance through the platform, the platform holds the technician's money.
 * The admin pays it out (outside the app) and records that here, once per booking.
 */
@Service
@Transactional
public class PayoutService {

    private final PayoutRepository payouts;
    private final PaymentRepository payments;
    private final BookingRepository bookings;

    public PayoutService(PayoutRepository payouts, PaymentRepository payments, BookingRepository bookings) {
        this.payouts = payouts;
        this.payments = payments;
        this.bookings = bookings;
    }

    private List<Payment> confirmedPlatformBalances() {
        return payments.findByTypeAndMethodAndStatusOrderByCreatedAtAsc(PaymentType.BALANCE, PaymentMethod.PLATFORM, PaymentStatus.CONFIRMED);
    }

    /** Confirmed platform balances that have not been paid out yet. */
    @Transactional(readOnly = true)
    public List<PendingPayoutView> pending() {
        return confirmedPlatformBalances().stream()
                .filter(p -> !payouts.existsByBooking(p.getBooking()))
                .map(p -> new PendingPayoutView(p.getBooking().getId(), p.getBooking().getTechnician().getId(),
                        p.getBooking().getTechnician().getFullName(), p.getAmount(), p.getId()))
                .toList();
    }

    public PayoutView record(Long bookingId, UserAccount admin, String reference, String note) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        Payment balance = confirmedPlatformBalances().stream()
                .filter(p -> p.getBooking().getId().equals(b.getId())).findFirst()
                .orElseThrow(() -> ApiException.conflict("This booking has no confirmed platform balance payment to pay out"));
        if (payouts.existsByBooking(b)) throw ApiException.conflict("A payout is already recorded for this booking");
        Payout p = new Payout();
        p.setBooking(b);
        p.setTechnician(b.getTechnician());
        p.setAmount(balance.getAmount());
        p.setReference(reference.trim());
        p.setNote(note);
        p.setPaidBy(admin);
        return view(payouts.save(p));
    }

    /** Technician: own payouts. Admin: all. */
    @Transactional(readOnly = true)
    public List<PayoutView> history(UserAccount user) {
        List<Payout> list = user.getRole() == Role.ADMIN ? payouts.findAllByOrderByPaidAtDesc()
                : payouts.findByTechnicianOrderByPaidAtDesc(user);
        return list.stream().map(PayoutService::view).toList();
    }

    static PayoutView view(Payout p) {
        return new PayoutView(p.getId(), p.getBooking().getId(), p.getTechnician().getId(), p.getTechnician().getFullName(),
                p.getAmount(), p.getReference(), p.getNote(), p.getPaidAt());
    }
}

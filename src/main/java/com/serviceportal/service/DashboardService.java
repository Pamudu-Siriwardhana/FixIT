package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.BookingRepository;
import com.serviceportal.repository.PaymentRepository;
import com.serviceportal.util.Pricing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** The technician's simple business-management view: jobs by status, earnings, upcoming work, customers. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ProfileService profiles;
    private final BookingService bookingService;
    private final Views views;

    public DashboardService(BookingRepository bookings, PaymentRepository payments, ProfileService profiles,
                            BookingService bookingService, Views views) {
        this.bookings = bookings;
        this.payments = payments;
        this.profiles = profiles;
        this.bookingService = bookingService;
        this.views = views;
    }

    @Transactional // not read-only: creates the technician profile row if it is missing
    public DashboardView forTechnician(UserAccount tech) {
        List<Booking> all = bookings.findByTechnicianOrderByCreatedAtDesc(tech);
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus s : BookingStatus.values()) counts.put(s, 0L);
        all.forEach(b -> counts.merge(b.getStatus(), 1L, Long::sum));

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        for (Booking b : all) {
            if (b.getAgreedPrice() == null) continue;
            BigDecimal payout = Pricing.technicianPayout(b.getAgreedPrice(), views.chargePercent());
            if (b.getStatus() == BookingStatus.PAID) earned = earned.add(payout);
            else if (b.getStatus() == BookingStatus.COMPLETED) pending = pending.add(payout);
        }
        BigDecimal chargePaid = BigDecimal.ZERO;
        for (Booking b : all) {
            for (Payment p : payments.findByBookingOrderByCreatedAtAscIdAsc(b)) {
                if (p.getType() == PaymentType.SERVICE_CHARGE && p.getStatus() == PaymentStatus.CONFIRMED) {
                    chargePaid = chargePaid.add(p.getAmount());
                }
            }
        }

        List<BookingView> upcoming = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(Booking::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(bookingService::view).toList();

        Map<Long, PartyView> customers = new LinkedHashMap<>();
        all.stream().filter(b -> b.getStatus().isConfirmedOrLater()).forEach(b ->
                customers.putIfAbsent(b.getCustomer().getId(), new PartyView(b.getCustomer().getId(),
                        b.getCustomer().getFullName(), b.getCustomer().getContactNumber())));

        TechnicianProfile tp = profiles.technicianProfile(tech);
        return new DashboardView(counts.entrySet().stream().map(e -> new StatusCount(e.getKey(), e.getValue())).toList(),
                earned, pending, chargePaid, tp.getRatingAverage(), tp.getRatingCount(), upcoming,
                new ArrayList<>(customers.values()));
    }
}

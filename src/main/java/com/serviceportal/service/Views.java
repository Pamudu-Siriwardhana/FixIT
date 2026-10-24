package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.util.Pricing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Maps entities to API views. */
@Component
public class Views {

    private final BigDecimal chargePercent;

    public Views(@Value("${app.service-charge-percent:10}") BigDecimal chargePercent) {
        this.chargePercent = chargePercent;
    }

    public BigDecimal chargePercent() {
        return chargePercent;
    }

    public List<LanguageView> languages(Set<Language> languages) {
        return languages.stream().sorted(Comparator.comparing(Language::getName)).map(LanguageView::of).toList();
    }

    public List<SkillView> skills(UserAccount u) {
        return u.getSkills().stream()
                .sorted(Comparator.comparing(s -> s.getProfession().getName()))
                .map(SkillView::of).toList();
    }

    public UserView user(UserAccount u, TechnicianProfile tp) {
        boolean tech = u.getRole() == Role.TECHNICIAN;
        return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getRejectionReason(), u.getContactNumber(), u.getGender(), u.getNic(), u.getAddress(),
                u.getLatitude(), u.getLongitude(), languages(u.getLanguages()),
                tp == null ? null : tp.getQualification(),
                tp == null ? null : tp.getExperienceYears(),
                tp == null ? null : tp.getRatingAverage(),
                tp == null ? null : tp.getRatingCount(),
                tech ? skills(u) : null);
    }

    public PreferenceView preference(CustomerPreference p) {
        return new PreferenceView(p.getPreferredGender(), p.getMaxDistanceKm(), p.getBudgetMin(), p.getBudgetMax(),
                p.getMinRating(), languages(p.getPreferredLanguages()));
    }

    private PartyView party(UserAccount u, boolean showContact) {
        return new PartyView(u.getId(), u.getFullName(), showContact ? u.getContactNumber() : null);
    }

    /** Contact numbers are only shown to the other side once the price is agreed. */
    public BookingView booking(Booking b, List<BidOffer> offers, Integer ratingScore) {
        boolean show = b.getStatus().isConfirmedOrLater();
        BigDecimal base = b.getAgreedPrice();
        return new BookingView(b.getId(), b.getStatus(), party(b.getCustomer(), show), party(b.getTechnician(), show),
                ProfessionView.of(b.getProfession()), b.getDescription(),
                b.getJobLatitude(), b.getJobLongitude(), b.getJobAddress(), b.getScheduledAt(),
                b.getCurrentOffer(), b.getCurrentOfferBy(), base,
                base == null ? null : Pricing.serviceCharge(base, chargePercent),
                base == null ? null : Pricing.technicianPayout(base, chargePercent),
                offers.stream().map(o -> new OfferView(o.getId(), o.getOfferedBy().getRole(),
                        o.getOfferedBy().getFullName(), o.getAmount(), o.getCreatedAt())).toList(),
                ratingScore, b.getCreatedAt());
    }

    public PaymentView payment(Payment p) {
        return new PaymentView(p.getId(), p.getBooking().getId(), p.getPayer().getId(), p.getPayer().getFullName(),
                p.getType(), p.getMethod(), p.getAmount(), p.getStatus(), p.hasProof(), p.getProofFileName(),
                p.getNote(), p.getCreatedAt(), p.getReviewedAt());
    }
}

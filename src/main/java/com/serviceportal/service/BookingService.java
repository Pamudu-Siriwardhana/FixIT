package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookings;
    private final BidOfferRepository offers;
    private final UserRepository users;
    private final ProfessionRepository professions;
    private final TechnicianSkillRepository skills;
    private final RatingRepository ratings;
    private final TechnicianProfileRepository techProfiles;
    private final ProfileService profiles;
    private final Views views;

    public BookingService(BookingRepository bookings, BidOfferRepository offers, UserRepository users,
                          ProfessionRepository professions, TechnicianSkillRepository skills, RatingRepository ratings,
                          TechnicianProfileRepository techProfiles, ProfileService profiles, Views views) {
        this.bookings = bookings;
        this.offers = offers;
        this.users = users;
        this.professions = professions;
        this.skills = skills;
        this.ratings = ratings;
        this.techProfiles = techProfiles;
        this.profiles = profiles;
        this.views = views;
    }

    // ---------- lookups ----------
    public Booking find(Long id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
    }

    /** The booking, if the user is the customer, the technician or an admin. */
    public Booking forParticipant(Long id, UserAccount user) {
        Booking b = find(id);
        if (user.getRole() != Role.ADMIN && !isParticipant(b, user)) {
            throw ApiException.forbidden("You are not part of this booking");
        }
        return b;
    }

    private static boolean isParticipant(Booking b, UserAccount u) {
        return b.getCustomer().getId().equals(u.getId()) || b.getTechnician().getId().equals(u.getId());
    }

    public BookingView view(Booking b) {
        Integer score = ratings.findByBooking(b).map(Rating::getScore).orElse(null);
        return views.booking(b, offers.findByBookingOrderByCreatedAtAscIdAsc(b), score);
    }

    @Transactional(readOnly = true)
    public List<BookingView> mine(UserAccount user) {
        List<Booking> list = user.getRole() == Role.CUSTOMER
                ? bookings.findByCustomerOrderByCreatedAtDesc(user)
                : user.getRole() == Role.TECHNICIAN ? bookings.findByTechnicianOrderByCreatedAtDesc(user)
                : bookings.findAll();
        return list.stream().map(this::view).toList();
    }

    // ---------- rules ----------
    /** An offer must sit inside the platform price range of the profession. */
    public static void validateInRange(Profession p, BigDecimal amount) {
        if (amount.compareTo(p.getMinPrice()) < 0 || amount.compareTo(p.getMaxPrice()) > 0) {
            throw ApiException.badRequest("Price must be between " + p.getMinPrice() + " and " + p.getMaxPrice()
                    + " for " + p.getName());
        }
    }

    /** Throws if the transition is not allowed by the job status workflow. */
    public static void requireTransition(Booking b, BookingStatus target) {
        if (!b.getStatus().canMoveTo(target)) {
            throw ApiException.conflict("Booking is " + b.getStatus() + "; cannot move to " + target);
        }
    }

    private void recordOffer(Booking b, UserAccount by, BigDecimal amount) {
        BidOffer o = new BidOffer();
        o.setBooking(b);
        o.setOfferedBy(by);
        o.setAmount(amount);
        offers.save(o);
        b.setCurrentOffer(amount);
        b.setCurrentOfferBy(by.getRole());
    }

    // ---------- actions ----------
    public BookingView create(UserAccount customer, BookingCreateRequest r) {
        CurrentUser.requireVerified(customer);
        UserAccount tech = users.findById(r.technicianId())
                .orElseThrow(() -> ApiException.badRequest("Unknown technician"));
        if (tech.getRole() != Role.TECHNICIAN) throw ApiException.badRequest("That user is not a technician");
        CurrentUser.requireVerified(tech);
        Profession p = professions.findById(r.professionId())
                .orElseThrow(() -> ApiException.badRequest("Unknown profession"));
        if (!skills.existsByTechnicianAndProfession(tech, p)) {
            throw ApiException.badRequest("This technician does not offer " + p.getName());
        }
        validateInRange(p, r.proposedPrice());

        Booking b = new Booking();
        b.setCustomer(customer);
        b.setTechnician(tech);
        b.setProfession(p);
        b.setDescription(r.description().trim());
        b.setJobLatitude(r.jobLatitude());
        b.setJobLongitude(r.jobLongitude());
        b.setJobAddress(r.jobAddress());
        b.setScheduledAt(r.scheduledAt());
        b.setStatus(BookingStatus.REQUESTED);
        bookings.save(b);
        recordOffer(b, customer, r.proposedPrice());
        return view(b);
    }

    /** Counter offer from the side that did not make the latest offer. */
    public BookingView counter(Booking b, UserAccount user, BigDecimal amount) {
        requireNegotiable(b, user);
        validateInRange(b.getProfession(), amount);
        requireTransition(b, BookingStatus.NEGOTIATING);
        recordOffer(b, user, amount);
        b.setStatus(BookingStatus.NEGOTIATING);
        return view(b);
    }

    /** Accepting the other side's latest offer fixes the agreed price. */
    public BookingView accept(Booking b, UserAccount user) {
        requireNegotiable(b, user);
        requireTransition(b, BookingStatus.CONFIRMED);
        b.setAgreedPrice(b.getCurrentOffer());
        b.setStatus(BookingStatus.CONFIRMED);
        return view(b);
    }

    private void requireNegotiable(Booking b, UserAccount user) {
        CurrentUser.requireVerified(user);
        if (!isParticipant(b, user)) throw ApiException.forbidden("You are not part of this booking");
        if (b.getStatus() != BookingStatus.REQUESTED && b.getStatus() != BookingStatus.NEGOTIATING) {
            throw ApiException.conflict("Booking is " + b.getStatus() + "; the price can no longer change");
        }
        if (b.getCurrentOfferBy() == user.getRole()) {
            throw ApiException.conflict("Waiting for the other side to respond to your offer");
        }
    }

    public BookingView cancel(Booking b, UserAccount user) {
        if (!isParticipant(b, user)) throw ApiException.forbidden("You are not part of this booking");
        requireTransition(b, BookingStatus.CANCELLED);
        b.setStatus(BookingStatus.CANCELLED);
        return view(b);
    }

    public BookingView start(Booking b, UserAccount tech) {
        requireTechnicianOf(b, tech);
        requireTransition(b, BookingStatus.IN_PROGRESS);
        b.setStatus(BookingStatus.IN_PROGRESS);
        return view(b);
    }

    public BookingView complete(Booking b, UserAccount tech) {
        requireTechnicianOf(b, tech);
        requireTransition(b, BookingStatus.COMPLETED);
        b.setStatus(BookingStatus.COMPLETED);
        return view(b);
    }

    private static void requireTechnicianOf(Booking b, UserAccount tech) {
        if (!b.getTechnician().getId().equals(tech.getId())) {
            throw ApiException.forbidden("Only the booked technician can do this");
        }
    }

    /** Called by the payment flow once both payments are confirmed. */
    public void markPaid(Booking b) {
        requireTransition(b, BookingStatus.PAID);
        b.setStatus(BookingStatus.PAID);
    }

    public RatingView rate(Booking b, UserAccount customer, RatingRequest r) {
        if (!b.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.forbidden("Only the customer of this booking can rate it");
        }
        if (b.getStatus() != BookingStatus.COMPLETED && b.getStatus() != BookingStatus.PAID) {
            throw ApiException.conflict("You can rate a technician once the job is completed");
        }
        if (ratings.existsByBooking(b)) throw ApiException.conflict("This booking is already rated");

        Rating rating = new Rating();
        rating.setBooking(b);
        rating.setCustomer(customer);
        rating.setTechnician(b.getTechnician());
        rating.setScore(r.score());
        rating.setComment(r.comment());
        ratings.save(rating);

        TechnicianProfile tp = profiles.technicianProfile(b.getTechnician());
        double total = tp.getRatingAverage() * tp.getRatingCount() + r.score();
        tp.setRatingCount(tp.getRatingCount() + 1);
        tp.setRatingAverage(Math.round(total / tp.getRatingCount() * 100.0) / 100.0);
        techProfiles.save(tp);
        return new RatingView(rating.getId(), b.getId(), rating.getScore(), rating.getComment(), rating.getCreatedAt());
    }
}

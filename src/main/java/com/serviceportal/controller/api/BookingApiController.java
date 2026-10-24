package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingApiController {

    private final CurrentUser currentUser;
    private final BookingService bookings;
    private final PaymentService payments;

    public BookingApiController(CurrentUser currentUser, BookingService bookings, PaymentService payments) {
        this.currentUser = currentUser;
        this.bookings = bookings;
        this.payments = payments;
    }

    /** Customer books a technician with a proposed price inside the profession's range. */
    @PostMapping
    public BookingView create(@Valid @RequestBody BookingCreateRequest r, Principal principal) {
        return bookings.create(currentUser.require(principal, Role.CUSTOMER), r);
    }

    @GetMapping
    public List<BookingView> mine(Principal principal) {
        return bookings.mine(currentUser.of(principal));
    }

    @GetMapping("/{id}")
    public BookingView get(@PathVariable Long id, Principal principal) {
        return bookings.view(bookings.forParticipant(id, currentUser.of(principal)));
    }

    /** Counter offer (must be inside the price range and from the side that did not make the last offer). */
    @PostMapping("/{id}/offers")
    public BookingView counter(@PathVariable Long id, @Valid @RequestBody OfferRequest r, Principal principal) {
        return bookings.counter(bookings.find(id), currentUser.of(principal), r.amount());
    }

    @PostMapping("/{id}/accept")
    public BookingView accept(@PathVariable Long id, Principal principal) {
        return bookings.accept(bookings.find(id), currentUser.of(principal));
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(@PathVariable Long id, Principal principal) {
        return bookings.cancel(bookings.find(id), currentUser.of(principal));
    }

    @PostMapping("/{id}/start")
    public BookingView start(@PathVariable Long id, Principal principal) {
        return bookings.start(bookings.find(id), currentUser.require(principal, Role.TECHNICIAN));
    }

    @PostMapping("/{id}/complete")
    public BookingView complete(@PathVariable Long id, Principal principal) {
        return bookings.complete(bookings.find(id), currentUser.require(principal, Role.TECHNICIAN));
    }

    @PostMapping("/{id}/rating")
    public RatingView rate(@PathVariable Long id, @Valid @RequestBody RatingRequest r, Principal principal) {
        return bookings.rate(bookings.find(id), currentUser.require(principal, Role.CUSTOMER), r);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentView> payments(@PathVariable Long id, Principal principal) {
        return payments.forBooking(bookings.forParticipant(id, currentUser.of(principal)));
    }

    /** Customer records a payment (multipart: type, method, optional note, file = proof for PLATFORM payments). */
    @PostMapping(value = "/{id}/payments", consumes = "multipart/form-data")
    public PaymentView submitPayment(@PathVariable Long id,
                                     @RequestParam PaymentType type,
                                     @RequestParam PaymentMethod method,
                                     @RequestParam(required = false) String note,
                                     @RequestParam(required = false) MultipartFile file,
                                     Principal principal) {
        return payments.submit(bookings.find(id), currentUser.require(principal, Role.CUSTOMER), type, method, note, file);
    }
}

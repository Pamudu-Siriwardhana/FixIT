package com.serviceportal.service;

import com.serviceportal.model.Booking;
import com.serviceportal.model.BookingStatus;
import com.serviceportal.model.Profession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BookingRulesTest {

    private final Profession electrician = new Profession("Electrician", new BigDecimal("1500"), new BigDecimal("25000"), "");

    @Test
    void priceMustBeInsideTheProfessionRange() {
        assertDoesNotThrow(() -> BookingService.validateInRange(electrician, new BigDecimal("1500")));
        assertDoesNotThrow(() -> BookingService.validateInRange(electrician, new BigDecimal("25000")));
        assertThrows(ApiException.class, () -> BookingService.validateInRange(electrician, new BigDecimal("1499.99")));
        assertThrows(ApiException.class, () -> BookingService.validateInRange(electrician, new BigDecimal("25000.01")));
    }

    @Test
    void jobStatusWorkflow() {
        assertTrue(BookingStatus.REQUESTED.canMoveTo(BookingStatus.NEGOTIATING));
        assertTrue(BookingStatus.NEGOTIATING.canMoveTo(BookingStatus.NEGOTIATING));
        assertTrue(BookingStatus.NEGOTIATING.canMoveTo(BookingStatus.CONFIRMED));
        assertTrue(BookingStatus.CONFIRMED.canMoveTo(BookingStatus.IN_PROGRESS));
        assertTrue(BookingStatus.IN_PROGRESS.canMoveTo(BookingStatus.COMPLETED));
        assertTrue(BookingStatus.COMPLETED.canMoveTo(BookingStatus.PAID));

        assertFalse(BookingStatus.REQUESTED.canMoveTo(BookingStatus.IN_PROGRESS));
        assertFalse(BookingStatus.IN_PROGRESS.canMoveTo(BookingStatus.CANCELLED));
        assertFalse(BookingStatus.PAID.canMoveTo(BookingStatus.CANCELLED));
        assertFalse(BookingStatus.CANCELLED.canMoveTo(BookingStatus.REQUESTED));
    }

    @Test
    void invalidTransitionIsRejectedWithConflict() {
        Booking b = new Booking();
        b.setStatus(BookingStatus.REQUESTED);
        ApiException e = assertThrows(ApiException.class, () -> BookingService.requireTransition(b, BookingStatus.COMPLETED));
        assertEquals(409, e.getStatus().value());
    }

    @Test
    void contactDetailsOnlyAfterConfirmation() {
        assertFalse(BookingStatus.NEGOTIATING.isConfirmedOrLater());
        assertTrue(BookingStatus.CONFIRMED.isConfirmedOrLater());
        assertTrue(BookingStatus.PAID.isConfirmedOrLater());
        assertFalse(BookingStatus.CANCELLED.isConfirmedOrLater());
    }
}

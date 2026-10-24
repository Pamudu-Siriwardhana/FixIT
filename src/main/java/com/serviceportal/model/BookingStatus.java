package com.serviceportal.model;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    REQUESTED, NEGOTIATING, CONFIRMED, IN_PROGRESS, COMPLETED, PAID, CANCELLED;

    public Set<BookingStatus> next() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(NEGOTIATING, CONFIRMED, CANCELLED);
            case NEGOTIATING -> EnumSet.of(NEGOTIATING, CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED);
            case COMPLETED -> EnumSet.of(PAID);
            case PAID, CANCELLED -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canMoveTo(BookingStatus target) {
        return next().contains(target);
    }

    /** True once both sides have agreed on a price (contact details become visible). */
    public boolean isConfirmedOrLater() {
        return this == CONFIRMED || this == IN_PROGRESS || this == COMPLETED || this == PAID;
    }
}

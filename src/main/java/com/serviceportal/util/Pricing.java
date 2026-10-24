package com.serviceportal.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Service charge maths: the platform keeps a percentage of the agreed price, deducted from the technician. */
public final class Pricing {
    private Pricing() {}

    public static BigDecimal serviceCharge(BigDecimal agreedPrice, BigDecimal percent) {
        return agreedPrice.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** What the technician keeps: agreed price minus the service charge. */
    public static BigDecimal technicianPayout(BigDecimal agreedPrice, BigDecimal percent) {
        return agreedPrice.subtract(serviceCharge(agreedPrice, percent)).setScale(2, RoundingMode.HALF_UP);
    }
}

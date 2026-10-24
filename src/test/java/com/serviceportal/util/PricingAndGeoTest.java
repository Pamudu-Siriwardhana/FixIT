package com.serviceportal.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingAndGeoTest {

    @Test
    void serviceChargeIsTenPercentAndPayoutIsTheRest() {
        BigDecimal price = new BigDecimal("12500");
        assertEquals(new BigDecimal("1250.00"), Pricing.serviceCharge(price, BigDecimal.TEN));
        assertEquals(new BigDecimal("11250.00"), Pricing.technicianPayout(price, BigDecimal.TEN));
    }

    @Test
    void chargeAndPayoutAlwaysAddUpToThePrice() {
        BigDecimal price = new BigDecimal("333.33");
        BigDecimal sum = Pricing.serviceCharge(price, BigDecimal.TEN).add(Pricing.technicianPayout(price, BigDecimal.TEN));
        assertEquals(0, price.compareTo(sum));
    }

    @Test
    void haversineColomboToKandyIsAboutNinetyFourKm() {
        double d = GeoUtil.distanceKm(6.9271, 79.8612, 7.2906, 80.6337);
        assertTrue(d > 92 && d < 96, "distance was " + d);
    }

    @Test
    void samePointIsZero() {
        assertEquals(0.0, GeoUtil.distanceKm(6.9, 79.8, 6.9, 79.8), 1e-9);
    }
}

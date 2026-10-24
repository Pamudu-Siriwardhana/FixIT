package com.serviceportal.service;

import com.serviceportal.model.Profession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryRulesTest {

    private final Profession p = new Profession("Plumber", new BigDecimal("1500"), new BigDecimal("20000"), "");

    @Test
    void budgetMustOverlapTheProfessionRange() {
        assertTrue(DiscoveryService.overlapsBudget(p, new BigDecimal("1000"), new BigDecimal("5000")));
        assertTrue(DiscoveryService.overlapsBudget(p, new BigDecimal("19000"), null));
        assertTrue(DiscoveryService.overlapsBudget(p, null, new BigDecimal("1500")));
        assertFalse(DiscoveryService.overlapsBudget(p, new BigDecimal("20001"), null));
        assertFalse(DiscoveryService.overlapsBudget(p, null, new BigDecimal("1000")));
    }
}

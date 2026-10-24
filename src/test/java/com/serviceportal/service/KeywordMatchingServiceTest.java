package com.serviceportal.service;

import com.serviceportal.model.Profession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeywordMatchingServiceTest {

    private final KeywordMatchingService matcher = new KeywordMatchingService();

    private static Profession p(String name, String keywords) {
        return new Profession(name, BigDecimal.ONE, BigDecimal.TEN, keywords);
    }

    private final List<Profession> all = List.of(
            p("Plumber", "pipe,leak,leaking,tap,sink,blocked"),
            p("Electrician", "socket,wiring,breaker,short circuit"),
            p("AC Technician", "ac,air conditioner,cooling"));

    @Test
    void picksThePlumberForALeakingTap() {
        var result = matcher.match("My kitchen tap is leaking and the sink is blocked", all);
        assertEquals("Plumber", result.get(0).profession().getName());
        assertEquals(1, result.size());
    }

    @Test
    void shortKeywordsOnlyMatchWholeWords() {
        assertTrue(matcher.match("a nice place for action", all).isEmpty());
        assertEquals("AC Technician", matcher.match("my ac is not cooling", all).get(0).profession().getName());
    }

    @Test
    void professionNameCountsMoreThanAKeyword() {
        var result = matcher.match("need an electrician, one socket sparks", all);
        assertEquals("Electrician", result.get(0).profession().getName());
        assertEquals(4, result.get(0).score());
    }

    @Test
    void noEvidenceMeansNoMatch() {
        assertTrue(matcher.match("I would like some help", all).isEmpty());
    }
}

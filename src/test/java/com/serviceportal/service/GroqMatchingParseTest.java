package com.serviceportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.model.Profession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroqMatchingParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static Profession p(long id, String name) {
        Profession x = new Profession(name, BigDecimal.ONE, BigDecimal.TEN, "pipe,leak");
        x.setId(id);
        return x;
    }

    private final List<Profession> all = List.of(p(1, "Plumber"), p(2, "Electrician"), p(3, "AC Technician"));

    @Test
    void parsesAPlainReplyBestFirst() throws IOException {
        var r = GroqMatchingService.parseMatches(mapper,
                "{\"professions\":[{\"name\":\"Electrician\",\"score\":4},{\"name\":\"plumber\",\"score\":9}]}", all);
        assertEquals(2, r.size());
        assertEquals("Plumber", r.get(0).profession().getName());
        assertEquals(9, r.get(0).score());
    }

    @Test
    void toleratesCodeFencesAndExtraText() throws IOException {
        var r = GroqMatchingService.parseMatches(mapper,
                "Sure!\n```json\n{\"professions\":[{\"name\":\"AC Technician\",\"score\":7}]}\n```", all);
        assertEquals("AC Technician", r.get(0).profession().getName());
    }

    @Test
    void ignoresUnknownNamesDuplicatesAndClampsScores() throws IOException {
        var r = GroqMatchingService.parseMatches(mapper,
                "{\"professions\":[{\"name\":\"Wizard\",\"score\":10},{\"name\":\"Plumber\",\"score\":99},{\"name\":\"Plumber\",\"score\":2}]}", all);
        assertEquals(1, r.size());
        assertEquals(10, r.get(0).score());
    }

    @Test
    void emptyListMeansNothingFits() throws IOException {
        assertTrue(GroqMatchingService.parseMatches(mapper, "{\"professions\":[]}", all).isEmpty());
    }

    @Test
    void replyWithoutJsonIsAnError() {
        assertThrows(IOException.class, () -> GroqMatchingService.parseMatches(mapper, "I cannot help", all));
    }

    @Test
    void withoutAKeyItFallsBackToKeywords() {
        var service = new GroqMatchingService("", "openai/gpt-oss-20b", "https://api.groq.com/openai/v1", mapper);
        var r = service.match("there is a leak and a bad pipe", all);
        assertEquals("Plumber", r.get(0).profession().getName());
    }
}

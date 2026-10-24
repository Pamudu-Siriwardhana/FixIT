package com.serviceportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.PlaceView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtrasParseTest {

    @Test
    void geocodeResponseUsesLongitudeFirst() throws IOException {
        String body = "{\"features\":[{\"geometry\":{\"coordinates\":[79.8612,6.9271]},\"properties\":{\"label\":\"Colombo, Sri Lanka\"}},"
                + "{\"geometry\":{\"coordinates\":[80.0]},\"properties\":{\"label\":\"broken\"}},"
                + "{\"geometry\":{\"coordinates\":[80.2,7.2]},\"properties\":{}}]}";
        List<PlaceView> r = OpenRouteServiceGeocodingService.parse(new ObjectMapper().readTree(body));
        assertEquals(2, r.size());
        assertEquals("Colombo, Sri Lanka", r.get(0).label());
        assertEquals(6.9271, r.get(0).latitude());
        assertEquals(79.8612, r.get(0).longitude());
        assertEquals("Unnamed place", r.get(1).label());
    }

    @Test
    void searchNeedsThreeCharactersAndAKey() {
        var svc = new OpenRouteServiceGeocodingService("", "http://localhost:1", "LK", new ObjectMapper());
        assertEquals(400, assertThrows(ApiException.class, () -> svc.search("ab")).getStatus().value());
        assertEquals(503, assertThrows(ApiException.class, () -> svc.search("colombo")).getStatus().value());
    }

    @Test
    void reviewersAreShownByFirstName() {
        assertEquals("Kamal", PublicProfileService.firstName("Kamal Perera"));
        assertEquals("Nimal", PublicProfileService.firstName("  Nimal "));
        assertEquals("Customer", PublicProfileService.firstName(""));
        assertEquals("Customer", PublicProfileService.firstName(null));
    }
}

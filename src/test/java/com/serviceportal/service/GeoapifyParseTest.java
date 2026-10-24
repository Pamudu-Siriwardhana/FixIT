package com.serviceportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.PlaceView;
import com.serviceportal.dto.Dtos.RouteView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoapifyParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void routeIsConvertedToKmMinutesStepsAndLatLngGeometry() throws IOException {
        String body = "{\"features\":[{\"properties\":{\"distance\":4230,\"time\":840,\"legs\":[{\"steps\":["
                + "{\"distance\":300,\"time\":60,\"instruction\":{\"text\":\"Turn left\"},\"name\":\"Galle Road\"},"
                + "{\"distance\":3930,\"time\":780,\"instruction\":{\"text\":\"Arrive\"}}]}]},"
                + "\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":[[[79.86,6.92],[79.87,6.93]],[[79.88,6.94]]]}}]}";
        RouteView r = GeoapifyRoutingService.parse(mapper.readTree(body), "DRIVING", "Car / taxi");
        assertNull(r.error());
        assertEquals(4.23, r.distanceKm());
        assertEquals(14.0, r.durationMinutes());
        assertEquals(2, r.steps().size());
        assertEquals("Turn left", r.steps().get(0).instruction());
        assertEquals("Galle Road", r.steps().get(0).road());
        assertEquals(3, r.geometry().size());
        assertArrayEquals(new double[]{6.92, 79.86}, r.geometry().get(0));   // [lat, lng]
    }

    @Test
    void plainLineStringIsAlsoAccepted() throws IOException {
        String body = "{\"features\":[{\"properties\":{\"distance\":1000,\"time\":600,\"legs\":[]},"
                + "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[79.86,6.92],[79.87,6.93]]}}]}";
        assertEquals(2, GeoapifyRoutingService.parse(mapper.readTree(body), "WALKING", "Walking").geometry().size());
    }

    @Test
    void emptyResponseMeansNoRoute() throws IOException {
        RouteView r = GeoapifyRoutingService.parse(mapper.readTree("{\"features\":[]}"), "DRIVING", "Car");
        assertEquals("No route found", r.error());
    }

    @Test
    void routingNeedsAKey() {
        var svc = new GeoapifyRoutingService("", "http://localhost:1", mapper);
        assertEquals(503, assertThrows(ApiException.class, () -> svc.routes(6.9, 79.8, 6.95, 79.87)).getStatus().value());
    }

    @Test
    void geocodeUsesPropertiesThenGeometry() throws IOException {
        String body = "{\"features\":[{\"properties\":{\"formatted\":\"Fort, Colombo\",\"lat\":6.93,\"lon\":79.84},\"geometry\":{\"coordinates\":[0,0]}},"
                + "{\"properties\":{\"formatted\":\"Kandy\"},\"geometry\":{\"coordinates\":[80.63,7.29]}},"
                + "{\"properties\":{},\"geometry\":{\"coordinates\":[]}}]}";
        List<PlaceView> r = GeoapifyGeocodingService.parse(mapper.readTree(body));
        assertEquals(2, r.size());
        assertEquals(6.93, r.get(0).latitude());
        assertEquals(79.84, r.get(0).longitude());
        assertEquals(7.29, r.get(1).latitude());
        assertEquals(80.63, r.get(1).longitude());
    }

    @Test
    void searchNeedsThreeCharactersAndAKey() {
        var svc = new GeoapifyGeocodingService("", "http://localhost:1", "LK", mapper);
        assertEquals(400, assertThrows(ApiException.class, () -> svc.search("ab")).getStatus().value());
        assertEquals(503, assertThrows(ApiException.class, () -> svc.search("colombo")).getStatus().value());
    }
}

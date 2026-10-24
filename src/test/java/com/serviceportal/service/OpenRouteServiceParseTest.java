package com.serviceportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.RouteView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouteServiceParseTest {

    private static final String SAMPLE = """
            {"features":[{"properties":{
               "summary":{"distance":5230.4,"duration":812.0},
               "segments":[{"steps":[
                  {"distance":1200.0,"duration":200.0,"instruction":"Head north on Galle Road","name":"Galle Road"},
                  {"distance":4030.4,"duration":612.0,"instruction":"Arrive at your destination","name":"-"}]}]},
             "geometry":{"type":"LineString","coordinates":[[79.85,6.92],[79.86,6.93],[79.87,6.95]]}}]}
            """;

    @Test
    void parsesDistanceDurationStepsAndGeometry() throws Exception {
        RouteView r = OpenRouteServiceRoutingService.parse(new ObjectMapper().readTree(SAMPLE), "DRIVING", "Car", null);
        assertEquals(5.23, r.distanceKm(), 1e-9);
        assertEquals(13.5, r.durationMinutes(), 1e-9);
        assertEquals(2, r.steps().size());
        assertEquals("Head north on Galle Road", r.steps().get(0).instruction());
        assertEquals(3, r.geometry().size());
        // geometry is returned as [lat, lng] for map libraries
        assertEquals(6.92, r.geometry().get(0)[0], 1e-9);
        assertEquals(79.85, r.geometry().get(0)[1], 1e-9);
        assertNull(r.error());
    }

    @Test
    void emptyResponseGivesAnErrorNotACrash() throws Exception {
        RouteView r = OpenRouteServiceRoutingService.parse(new ObjectMapper().readTree("{}"), "WALKING", "Walking", null);
        assertNotNull(r.error());
        assertNull(r.distanceKm());
    }
}

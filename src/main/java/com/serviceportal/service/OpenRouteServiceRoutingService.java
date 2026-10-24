package com.serviceportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.RouteStep;
import com.serviceportal.dto.Dtos.RouteView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenStreetMap data through OpenRouteService. Gives car, cycling and walking routes with distance, duration and
 * turn-by-turn steps. There is no public transport data and no motorbike profile: the motorbike entry is the
 * car route used as an estimate.
 */
@Service
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "ors")
public class OpenRouteServiceRoutingService implements RoutingService {

    public static final String TRANSIT_NOTE = "Bus and train routes, fares and timetables are not available (OpenRouteService has no public transport data).";

    private final RestClient client;
    private final String apiKey;
    private final ObjectMapper mapper;

    public OpenRouteServiceRoutingService(@Value("${app.routing.ors.api-key:}") String apiKey,
                                          @Value("${app.routing.ors.base-url:https://api.openrouteservice.org}") String baseUrl,
                                          ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    @Override
    public List<RouteView> routes(double fromLat, double fromLng, double toLat, double toLng) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Routing is not configured. Set app.routing.ors.api-key (or the ORS_API_KEY environment variable).");
        }
        List<RouteView> out = new ArrayList<>();
        RouteView driving = fetch("DRIVING", "Car / taxi", "driving-car", null, fromLat, fromLng, toLat, toLng);
        out.add(driving);
        out.add(new RouteView("MOTORBIKE", "Motorbike / tuk-tuk (estimate)",
                "Estimate based on the car route; the routing provider has no motorbike or tuk-tuk profile.",
                driving.distanceKm(), driving.durationMinutes(), driving.steps(), driving.geometry(), driving.error()));
        out.add(fetch("CYCLING", "Bicycle", "cycling-regular", null, fromLat, fromLng, toLat, toLng));
        out.add(fetch("WALKING", "Walking", "foot-walking", null, fromLat, fromLng, toLat, toLng));
        return out;
    }

    private RouteView fetch(String mode, String label, String profile, String note,
                            double fromLat, double fromLng, double toLat, double toLng) {
        try {
            String body = client.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("coordinates", List.of(List.of(fromLng, fromLat), List.of(toLng, toLat)),
                            "instructions", true))
                    .retrieve()
                    .body(String.class);
            return parse(mapper.readTree(body), mode, label, note);
        } catch (Exception e) {
            return new RouteView(mode, label, note, null, null, List.of(), List.of(), "Route unavailable: " + e.getMessage());
        }
    }

    /** Turns an OpenRouteService GeoJSON directions response into a RouteView (geometry as [lat, lng] pairs). */
    static RouteView parse(JsonNode root, String mode, String label, String note) {
        JsonNode feature = root.path("features").path(0);
        JsonNode props = feature.path("properties");
        JsonNode summary = props.path("summary");
        if (summary.isMissingNode()) {
            return new RouteView(mode, label, note, null, null, List.of(), List.of(), "No route found");
        }
        List<RouteStep> steps = new ArrayList<>();
        for (JsonNode seg : props.path("segments")) {
            for (JsonNode s : seg.path("steps")) {
                steps.add(new RouteStep(s.path("instruction").asText(), s.path("name").asText(""),
                        s.path("distance").asDouble(), s.path("duration").asDouble()));
            }
        }
        List<double[]> geometry = new ArrayList<>();
        for (JsonNode c : feature.path("geometry").path("coordinates")) {
            geometry.add(new double[]{c.path(1).asDouble(), c.path(0).asDouble()});
        }
        double km = Math.round(summary.path("distance").asDouble() / 10.0) / 100.0;
        double min = Math.round(summary.path("duration").asDouble() / 6.0) / 10.0;
        return new RouteView(mode, label, note, km, min, steps, geometry, null);
    }
}

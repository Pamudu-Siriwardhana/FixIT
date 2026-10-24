package com.serviceportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.RouteStep;
import com.serviceportal.dto.Dtos.RouteView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes from Geoapify (OpenStreetMap data): car, motorbike, bicycle and walking with distance, duration and
 * turn-by-turn steps. Bus and train timetables are not included.
 */
@Service
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "geoapify", matchIfMissing = true)
public class GeoapifyRoutingService implements RoutingService {

    private final RestClient client;
    private final String apiKey;
    private final ObjectMapper mapper;

    public GeoapifyRoutingService(@Value("${app.geoapify.api-key:}") String apiKey,
                                  @Value("${app.geoapify.base-url:https://api.geoapify.com}") String baseUrl,
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
                    "Routing is not configured. Set the GEOAPIFY_API_KEY environment variable (or app.geoapify.api-key).");
        }
        List<RouteView> out = new ArrayList<>();
        out.add(fetch("DRIVING", "Car / taxi", "drive", fromLat, fromLng, toLat, toLng));
        out.add(fetch("MOTORBIKE", "Motorbike", "motorcycle", fromLat, fromLng, toLat, toLng));
        out.add(fetch("CYCLING", "Bicycle", "bicycle", fromLat, fromLng, toLat, toLng));
        out.add(fetch("WALKING", "Walking", "walk", fromLat, fromLng, toLat, toLng));
        return out;
    }

    private RouteView fetch(String mode, String label, String geoapifyMode,
                            double fromLat, double fromLng, double toLat, double toLng) {
        try {
            String body = client.get()
                    .uri(u -> u.path("/v1/routing")
                            .queryParam("waypoints", fromLat + "," + fromLng + "|" + toLat + "," + toLng)
                            .queryParam("mode", geoapifyMode)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve().body(String.class);
            return parse(mapper.readTree(body), mode, label);
        } catch (Exception e) {
            return new RouteView(mode, label, null, null, null, List.of(), List.of(), "Route unavailable: " + e.getMessage());
        }
    }

    /** Turns a Geoapify GeoJSON route into a RouteView (geometry as [lat, lng] pairs). Distances are metres, times seconds. */
    static RouteView parse(JsonNode root, String mode, String label) {
        JsonNode feature = root.path("features").path(0);
        JsonNode props = feature.path("properties");
        if (props.isMissingNode() || !props.has("distance")) {
            return new RouteView(mode, label, null, null, null, List.of(), List.of(), "No route found");
        }
        List<RouteStep> steps = new ArrayList<>();
        for (JsonNode leg : props.path("legs")) {
            for (JsonNode s : leg.path("steps")) {
                steps.add(new RouteStep(s.path("instruction").path("text").asText(""), s.path("name").asText(""),
                        s.path("distance").asDouble(), s.path("time").asDouble()));
            }
        }
        List<double[]> geometry = new ArrayList<>();
        JsonNode geo = feature.path("geometry");
        boolean multi = "MultiLineString".equals(geo.path("type").asText());
        for (JsonNode line : multi ? geo.path("coordinates") : List.of(geo.path("coordinates"))) {
            for (JsonNode c : line) {
                geometry.add(new double[]{c.path(1).asDouble(), c.path(0).asDouble()});   // GeoJSON is [lng, lat]
            }
        }
        double km = Math.round(props.path("distance").asDouble() / 10.0) / 100.0;
        double min = Math.round(props.path("time").asDouble() / 6.0) / 10.0;
        return new RouteView(mode, label, null, km, min, steps, geometry, null);
    }
}

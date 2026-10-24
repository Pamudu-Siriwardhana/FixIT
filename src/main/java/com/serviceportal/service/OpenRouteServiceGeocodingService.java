package com.serviceportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.PlaceView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Address search with OpenRouteService (Pelias), limited to one country (Sri Lanka by default). */
@Service
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "ors")
public class OpenRouteServiceGeocodingService implements GeocodingService {

    private final RestClient client;
    private final String apiKey;
    private final String country;
    private final ObjectMapper mapper;

    public OpenRouteServiceGeocodingService(@Value("${app.routing.ors.api-key:}") String apiKey,
                                            @Value("${app.routing.ors.base-url:https://api.openrouteservice.org}") String baseUrl,
                                            @Value("${app.geocoding.country:LK}") String country,
                                            ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.country = country;
        this.mapper = mapper;
    }

    @Override
    public List<PlaceView> search(String text) {
        if (text == null || text.trim().length() < 3) throw ApiException.badRequest("Type at least 3 characters");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address search is not configured. Set app.routing.ors.api-key (or the ORS_API_KEY environment variable). You can still click the map.");
        }
        try {
            String body = client.get()
                    .uri(u -> {
                        var b = u.path("/geocode/search").queryParam("api_key", apiKey).queryParam("text", text.trim()).queryParam("size", 5);
                        if (country != null && !country.isBlank()) b = b.queryParam("boundary.country", country);
                        return b.build();
                    })
                    .retrieve().body(String.class);
            return parse(mapper.readTree(body));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Address search failed: " + e.getMessage());
        }
    }

    /** GeoJSON coordinates are [longitude, latitude]. */
    static List<PlaceView> parse(JsonNode root) {
        List<PlaceView> out = new ArrayList<>();
        for (JsonNode f : root.path("features")) {
            JsonNode c = f.path("geometry").path("coordinates");
            if (c.size() < 2) continue;
            String label = f.path("properties").path("label").asText("").trim();
            out.add(new PlaceView(label.isEmpty() ? "Unnamed place" : label, c.get(1).asDouble(), c.get(0).asDouble()));
        }
        return out;
    }
}

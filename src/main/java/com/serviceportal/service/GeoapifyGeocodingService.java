package com.serviceportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.dto.Dtos.PlaceView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Address search with Geoapify, limited to one country (Sri Lanka by default). */
@Service
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "geoapify", matchIfMissing = true)
public class GeoapifyGeocodingService implements GeocodingService {

    private final RestClient client;
    private final String apiKey;
    private final String country;
    private final ObjectMapper mapper;

    public GeoapifyGeocodingService(@Value("${app.geoapify.api-key:}") String apiKey,
                                    @Value("${app.geoapify.base-url:https://api.geoapify.com}") String baseUrl,
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
                    "Address search is not configured. Set the GEOAPIFY_API_KEY environment variable. You can still click the map.");
        }
        try {
            String body = client.get()
                    .uri(u -> {
                        var b = u.path("/v1/geocode/search").queryParam("text", text.trim())
                                .queryParam("limit", 5).queryParam("format", "geojson").queryParam("apiKey", apiKey);
                        if (country != null && !country.isBlank()) b = b.queryParam("filter", "countrycode:" + country.toLowerCase());
                        return b.build();
                    })
                    .retrieve().body(String.class);
            return parse(mapper.readTree(body));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Address search failed: " + e.getMessage());
        }
    }

    /** Uses the lat/lon properties, or the GeoJSON point ([lng, lat]) when they are missing. */
    static List<PlaceView> parse(JsonNode root) {
        List<PlaceView> out = new ArrayList<>();
        for (JsonNode f : root.path("features")) {
            JsonNode p = f.path("properties");
            JsonNode c = f.path("geometry").path("coordinates");
            double lat, lng;
            if (p.has("lat") && p.has("lon")) {
                lat = p.get("lat").asDouble();
                lng = p.get("lon").asDouble();
            } else if (c.size() >= 2) {
                lat = c.get(1).asDouble();
                lng = c.get(0).asDouble();
            } else {
                continue;
            }
            String label = p.path("formatted").asText("").trim();
            out.add(new PlaceView(label.isEmpty() ? "Unnamed place" : label, lat, lng));
        }
        return out;
    }
}

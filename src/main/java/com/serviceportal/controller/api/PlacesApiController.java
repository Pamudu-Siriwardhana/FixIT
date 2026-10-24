package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.PlaceView;
import com.serviceportal.dto.Dtos.TechnicianPublicView;
import com.serviceportal.service.GeocodingService;
import com.serviceportal.service.PublicProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Address search for the map pin, and public technician profiles. Any signed-in user. */
@RestController
@RequestMapping("/api")
public class PlacesApiController {

    private final GeocodingService geocoding;
    private final PublicProfileService profiles;

    public PlacesApiController(GeocodingService geocoding, PublicProfileService profiles) {
        this.geocoding = geocoding;
        this.profiles = profiles;
    }

    @GetMapping("/geocode")
    public List<PlaceView> geocode(@RequestParam("q") String q) {
        return geocoding.search(q);
    }

    @GetMapping("/technicians/{id}")
    public TechnicianPublicView technician(@PathVariable Long id) {
        return profiles.technician(id);
    }
}

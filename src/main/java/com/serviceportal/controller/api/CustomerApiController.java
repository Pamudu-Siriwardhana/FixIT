package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.Role;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.DiscoveryService;
import com.serviceportal.service.DiscoveryService.SearchCriteria;
import com.serviceportal.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CustomerApiController {

    private final CurrentUser currentUser;
    private final ProfileService profiles;
    private final DiscoveryService discovery;

    public CustomerApiController(CurrentUser currentUser, ProfileService profiles, DiscoveryService discovery) {
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.discovery = discovery;
    }

    @GetMapping("/customer/preferences")
    public PreferenceView preferences(Principal principal) {
        return profiles.getPreference(currentUser.require(principal, Role.CUSTOMER));
    }

    @PutMapping("/customer/preferences")
    public PreferenceView updatePreferences(@Valid @RequestBody PreferenceRequest r, Principal principal) {
        return profiles.updatePreference(currentUser.require(principal, Role.CUSTOMER), r);
    }

    /**
     * Nearby verified technicians, nearest first. lat/lng default to the saved profile location; filters default
     * to the saved preferences unless usePreferences=false.
     */
    @GetMapping("/discovery/technicians")
    public List<TechnicianResult> nearby(@RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lng,
                                         @RequestParam(required = false) Double radiusKm,
                                         @RequestParam(required = false) Long professionId,
                                         @RequestParam(required = false) Long languageId,
                                         @RequestParam(required = false) String gender,
                                         @RequestParam(required = false) Double minRating,
                                         @RequestParam(defaultValue = "true") boolean usePreferences,
                                         Principal principal) {
        return discovery.nearby(currentUser.require(principal, Role.CUSTOMER),
                new SearchCriteria(lat, lng, radiusKm, professionId, languageId, gender, minRating, usePreferences));
    }

    /** Describe a problem; the matcher picks the best-fit profession and returns nearby technicians for it. */
    @PostMapping("/discovery/match")
    public MatchResponse match(@Valid @RequestBody MatchRequest r, Principal principal) {
        return discovery.match(currentUser.require(principal, Role.CUSTOMER), r);
    }
}

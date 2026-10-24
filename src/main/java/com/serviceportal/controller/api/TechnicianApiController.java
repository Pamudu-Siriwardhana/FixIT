package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.Booking;
import com.serviceportal.model.Role;
import com.serviceportal.model.UserAccount;
import com.serviceportal.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/technician")
public class TechnicianApiController {

    private final CurrentUser currentUser;
    private final ProfileService profiles;
    private final DashboardService dashboard;
    private final BookingService bookings;
    private final RoutingService routing;

    public TechnicianApiController(CurrentUser currentUser, ProfileService profiles, DashboardService dashboard,
                                   BookingService bookings, RoutingService routing) {
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.dashboard = dashboard;
        this.bookings = bookings;
        this.routing = routing;
    }

    @PutMapping("/profile")
    public UserView profile(@Valid @RequestBody TechnicianProfileRequest r, Principal principal) {
        return profiles.updateTechnicianProfile(currentUser.require(principal, Role.TECHNICIAN), r);
    }

    /** Add (or update) a profession with skill level and qualification notes. */
    @PostMapping("/skills")
    public UserView addSkill(@Valid @RequestBody SkillRequest r, Principal principal) {
        return profiles.addSkill(currentUser.require(principal, Role.TECHNICIAN), r);
    }

    @DeleteMapping("/skills/{id}")
    public UserView removeSkill(@PathVariable Long id, Principal principal) {
        return profiles.removeSkill(currentUser.require(principal, Role.TECHNICIAN), id);
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(Principal principal) {
        return dashboard.forTechnician(currentUser.require(principal, Role.TECHNICIAN));
    }

    /**
     * Routes from the technician to the job location: distance, time and step-by-step directions for car,
     * motorbike (estimate), bicycle and walking. Start point is fromLat/fromLng (current GPS) or the saved location.
     */
    @GetMapping("/bookings/{id}/routes")
    public RoutesResponse routes(@PathVariable Long id,
                                 @RequestParam(required = false) Double fromLat,
                                 @RequestParam(required = false) Double fromLng,
                                 @RequestParam(required = false) String mode,
                                 Principal principal) {
        UserAccount tech = currentUser.require(principal, Role.TECHNICIAN);
        Booking b = bookings.forParticipant(id, tech);
        Double lat = fromLat != null ? fromLat : tech.getLatitude();
        Double lng = fromLng != null ? fromLng : tech.getLongitude();
        if (lat == null || lng == null) {
            throw ApiException.badRequest("Provide fromLat and fromLng, or save your location on your profile first");
        }
        List<RouteView> routes = routing.routes(lat, lng, b.getJobLatitude(), b.getJobLongitude());
        if (mode != null) {
            routes = routes.stream().filter(r -> r.mode().equalsIgnoreCase(mode)).toList();
            if (routes.isEmpty()) throw ApiException.badRequest("mode must be DRIVING, MOTORBIKE, CYCLING or WALKING");
        }
        return new RoutesResponse(b.getId(), lat, lng, b.getJobLatitude(), b.getJobLongitude(), b.getJobAddress(),
                routes, RoutingService.TRANSIT_NOTE);
    }
}

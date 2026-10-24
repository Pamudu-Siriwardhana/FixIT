package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Any signed-in user: own profile, map location, languages, and the shared reference lists. */
@RestController
@RequestMapping("/api")
public class MeApiController {

    private final CurrentUser currentUser;
    private final ProfileService profiles;

    public MeApiController(CurrentUser currentUser, ProfileService profiles) {
        this.currentUser = currentUser;
        this.profiles = profiles;
    }

    @GetMapping("/me")
    public UserView me(Principal principal) {
        return profiles.me(currentUser.of(principal));
    }

    /** Edit name, contact number, gender and address. */
    @PutMapping("/me")
    public UserView details(@Valid @RequestBody UpdateDetailsRequest r, Principal principal) {
        return profiles.updateDetails(currentUser.of(principal), r);
    }

    /** Save the map pin (latitude/longitude) for the profile. */
    @PutMapping("/me/location")
    public UserView location(@Valid @RequestBody LocationRequest r, Principal principal) {
        return profiles.setLocation(currentUser.of(principal), r);
    }

    @PutMapping("/me/languages")
    public UserView languages(@Valid @RequestBody LanguagesRequest r, Principal principal) {
        return profiles.setLanguages(currentUser.of(principal), r);
    }

    @GetMapping("/languages")
    public List<LanguageView> languages() {
        return profiles.allLanguages();
    }

    @GetMapping("/professions")
    public List<ProfessionView> professions() {
        return profiles.allProfessions();
    }
}

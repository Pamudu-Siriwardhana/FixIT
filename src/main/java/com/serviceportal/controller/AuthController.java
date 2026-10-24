package com.serviceportal.controller;

import com.serviceportal.dto.RegistrationForm;
import com.serviceportal.model.Language;
import com.serviceportal.model.Role;
import com.serviceportal.model.UserAccount;
import com.serviceportal.model.VerificationStatus;
import com.serviceportal.repository.LanguageRepository;
import com.serviceportal.repository.UserRepository;
import com.serviceportal.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class AuthController {

    private final UserRepository users;
    private final LanguageRepository languageRepository;
    private final ProfileService profiles;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, LanguageRepository languageRepository, ProfileService profiles,
                          PasswordEncoder encoder) {
        this.users = users;
        this.languageRepository = languageRepository;
        this.profiles = profiles;
        this.encoder = encoder;
    }

    @ModelAttribute("genders")
    public List<String> genders() {
        return List.of("Male", "Female", "Other");
    }

    @ModelAttribute("languages")
    public List<Language> languages() {
        return languageRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result) {
        // Admins are never self-registered.
        if (form.getRole() == Role.ADMIN) {
            result.rejectValue("role", "forbidden", "Select Customer or Technician");
        }
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (form.getEmail() != null && users.existsByEmail(form.getEmail().trim().toLowerCase())) {
            result.rejectValue("email", "duplicate", "An account with this email already exists");
        }
        if (form.getNic() != null && users.existsByNic(form.getNic().trim().toUpperCase())) {
            result.rejectValue("nic", "duplicate", "This NIC is already registered");
        }
        Set<Language> chosen = new HashSet<>();
        if (form.getLanguageIds() != null) {
            for (Long id : form.getLanguageIds()) {
                languageRepository.findById(id).ifPresentOrElse(chosen::add,
                        () -> result.rejectValue("languageIds", "unknown", "Unknown language selected"));
            }
        }
        if (result.hasErrors()) {
            return "register";
        }

        UserAccount u = new UserAccount();
        u.setRole(form.getRole());
        u.setStatus(VerificationStatus.PENDING);
        u.setFullName(form.getFullName().trim());
        u.setEmail(form.getEmail().trim().toLowerCase());
        u.setContactNumber(form.getContactNumber().trim());
        u.setGender(form.getGender());
        u.setLanguages(chosen);
        u.setNic(form.getNic().trim().toUpperCase());
        u.setAddress(form.getAddress().trim());
        u.setPassword(encoder.encode(form.getPassword()));
        users.save(u);
        if (u.getRole() == Role.TECHNICIAN) profiles.technicianProfile(u);
        else profiles.preference(u);
        return "redirect:/login?registered";
    }
}

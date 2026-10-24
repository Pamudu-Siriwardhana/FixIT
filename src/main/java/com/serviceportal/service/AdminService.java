package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdminService {

    private final UserRepository users;
    private final LanguageRepository languages;
    private final ProfessionRepository professions;
    private final TechnicianProfileRepository techProfiles;
    private final Views views;

    public AdminService(UserRepository users, LanguageRepository languages, ProfessionRepository professions,
                        TechnicianProfileRepository techProfiles, Views views) {
        this.users = users;
        this.languages = languages;
        this.professions = professions;
        this.techProfiles = techProfiles;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<UserView> usersByStatus(VerificationStatus status) {
        return users.findByStatusOrderByIdAsc(status).stream()
                .map(u -> views.user(u, u.getRole() == Role.TECHNICIAN ? techProfiles.findByUser(u).orElse(null) : null))
                .toList();
    }

    public UserView approve(Long userId) {
        UserAccount u = target(userId);
        u.setStatus(VerificationStatus.VERIFIED);
        u.setRejectionReason(null);
        return view(users.save(u));
    }

    public UserView reject(Long userId, String reason) {
        UserAccount u = target(userId);
        u.setStatus(VerificationStatus.REJECTED);
        u.setRejectionReason(reason);
        return view(users.save(u));
    }

    private UserAccount target(Long id) {
        UserAccount u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == Role.ADMIN) throw ApiException.badRequest("Admin accounts are not subject to verification");
        return u;
    }

    private UserView view(UserAccount u) {
        return views.user(u, u.getRole() == Role.TECHNICIAN ? techProfiles.findByUser(u).orElse(null) : null);
    }

    public LanguageView addLanguage(String name) {
        String n = name.trim();
        if (languages.existsByNameIgnoreCase(n)) throw ApiException.conflict("Language already exists");
        return LanguageView.of(languages.save(new Language(n)));
    }

    public ProfessionView saveProfession(Long id, ProfessionRequest r) {
        if (r.minPrice().compareTo(r.maxPrice()) > 0) throw ApiException.badRequest("minPrice cannot exceed maxPrice");
        Profession p;
        if (id == null) {
            if (professions.existsByNameIgnoreCase(r.name().trim())) throw ApiException.conflict("Profession already exists");
            p = new Profession();
        } else {
            p = professions.findById(id).orElseThrow(() -> ApiException.notFound("Profession not found"));
        }
        p.setName(r.name().trim());
        p.setMinPrice(r.minPrice());
        p.setMaxPrice(r.maxPrice());
        p.setKeywords(r.keywords());
        return ProfessionView.of(professions.save(p));
    }
}

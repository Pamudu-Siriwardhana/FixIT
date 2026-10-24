package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ProfileService {

    private final UserRepository users;
    private final LanguageRepository languages;
    private final ProfessionRepository professions;
    private final TechnicianSkillRepository skills;
    private final TechnicianProfileRepository techProfiles;
    private final CustomerPreferenceRepository preferences;
    private final Views views;

    public ProfileService(UserRepository users, LanguageRepository languages, ProfessionRepository professions,
                          TechnicianSkillRepository skills, TechnicianProfileRepository techProfiles,
                          CustomerPreferenceRepository preferences, Views views) {
        this.users = users;
        this.languages = languages;
        this.professions = professions;
        this.skills = skills;
        this.techProfiles = techProfiles;
        this.preferences = preferences;
        this.views = views;
    }

    /** A rejected user who edits their profile goes back into the verification queue. */
    private void resubmitIfRejected(UserAccount u) {
        if (u.getStatus() == VerificationStatus.REJECTED) {
            u.setStatus(VerificationStatus.PENDING);
            u.setRejectionReason(null);
        }
    }

    public TechnicianProfile technicianProfile(UserAccount u) {
        return techProfiles.findByUser(u).orElseGet(() -> {
            TechnicianProfile tp = new TechnicianProfile();
            tp.setUser(u);
            return techProfiles.save(tp);
        });
    }

    public CustomerPreference preference(UserAccount u) {
        return preferences.findByUser(u).orElseGet(() -> {
            CustomerPreference p = new CustomerPreference();
            p.setUser(u);
            return preferences.save(p);
        });
    }

    @Transactional(readOnly = true)
    public UserView me(UserAccount u) {
        TechnicianProfile tp = u.getRole() == Role.TECHNICIAN ? techProfiles.findByUser(u).orElse(null) : null;
        return views.user(u, tp);
    }

    public UserView setLocation(UserAccount u, LocationRequest r) {
        u.setLatitude(r.latitude());
        u.setLongitude(r.longitude());
        if (r.address() != null && !r.address().isBlank()) {
            u.setAddress(r.address().trim());
        }
        resubmitIfRejected(u);
        users.save(u);
        return me(u);
    }

    /** Name, contact number and gender (address is saved with the map location). Email and NIC identify the account and cannot be changed here. */
    public UserView updateDetails(UserAccount u, UpdateDetailsRequest r) {
        u.setFullName(r.fullName().trim());
        u.setContactNumber(r.contactNumber().trim());
        u.setGender(r.gender().trim());
        resubmitIfRejected(u);
        users.save(u);
        return me(u);
    }

    public Set<Language> resolveLanguages(Set<Long> ids) {
        Set<Language> found = new HashSet<>();
        if (ids == null) return found;
        for (Long id : ids) {
            found.add(languages.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown language id " + id)));
        }
        return found;
    }

    public UserView setLanguages(UserAccount u, LanguagesRequest r) {
        u.setLanguages(resolveLanguages(r.languageIds()));
        resubmitIfRejected(u);
        users.save(u);
        return me(u);
    }

    public UserView updateTechnicianProfile(UserAccount u, TechnicianProfileRequest r) {
        TechnicianProfile tp = technicianProfile(u);
        tp.setQualification(r.qualification());
        tp.setExperienceYears(r.experienceYears());
        techProfiles.save(tp);
        resubmitIfRejected(u);
        users.save(u);
        return me(u);
    }

    public UserView addSkill(UserAccount tech, SkillRequest r) {
        Profession p = professions.findById(r.professionId())
                .orElseThrow(() -> ApiException.badRequest("Unknown profession id " + r.professionId()));
        TechnicianSkill s = skills.findByTechnicianAndProfession(tech, p).orElseGet(() -> {
            TechnicianSkill n = new TechnicianSkill();
            n.setTechnician(tech);
            n.setProfession(p);
            tech.getSkills().add(n);
            return n;
        });
        s.setSkillLevel(r.skillLevel());
        s.setNotes(r.notes());
        skills.save(s);
        resubmitIfRejected(tech);
        users.save(tech);
        return me(tech);
    }

    public UserView removeSkill(UserAccount tech, Long skillId) {
        boolean removed = tech.getSkills().removeIf(s -> s.getId().equals(skillId));
        if (!removed) throw ApiException.notFound("Skill not found");
        users.save(tech);
        return me(tech);
    }

    @Transactional(readOnly = true)
    public PreferenceView getPreference(UserAccount u) {
        return views.preference(preferences.findByUser(u).orElseGet(CustomerPreference::new));
    }

    public PreferenceView updatePreference(UserAccount u, PreferenceRequest r) {
        if (r.budgetMin() != null && r.budgetMax() != null && r.budgetMin().compareTo(r.budgetMax()) > 0) {
            throw ApiException.badRequest("budgetMin cannot be greater than budgetMax");
        }
        CustomerPreference p = preference(u);
        p.setPreferredGender(r.preferredGender() == null || r.preferredGender().isBlank() ? null : r.preferredGender());
        p.setMaxDistanceKm(r.maxDistanceKm());
        p.setBudgetMin(r.budgetMin());
        p.setBudgetMax(r.budgetMax());
        p.setMinRating(r.minRating());
        p.setPreferredLanguages(resolveLanguages(r.preferredLanguageIds()));
        return views.preference(preferences.save(p));
    }

    @Transactional(readOnly = true)
    public List<LanguageView> allLanguages() {
        return languages.findAllByOrderByNameAsc().stream().map(LanguageView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfessionView> allProfessions() {
        return professions.findAllByOrderByNameAsc().stream().map(ProfessionView::of).toList();
    }
}

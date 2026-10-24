package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import com.serviceportal.util.GeoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DiscoveryService {

    public static final double DEFAULT_RADIUS_KM = 10.0;

    public record SearchCriteria(Double lat, Double lng, Double radiusKm, Long professionId, Long languageId,
                                 String gender, Double minRating, boolean usePreferences) {}

    private final UserRepository users;
    private final TechnicianProfileRepository techProfiles;
    private final CustomerPreferenceRepository preferences;
    private final ProfessionRepository professions;
    private final MatchingService matching;
    private final Views views;

    public DiscoveryService(UserRepository users, TechnicianProfileRepository techProfiles,
                            CustomerPreferenceRepository preferences, ProfessionRepository professions,
                            MatchingService matching, Views views) {
        this.users = users;
        this.techProfiles = techProfiles;
        this.preferences = preferences;
        this.professions = professions;
        this.matching = matching;
        this.views = views;
    }

    /** Verified technicians near the customer, filtered by criteria and (optionally) saved preferences. */
    public List<TechnicianResult> nearby(UserAccount customer, SearchCriteria c) {
        CurrentUser.requireVerified(customer);
        CustomerPreference pref = c.usePreferences() ? preferences.findByUser(customer).orElse(null) : null;

        Double lat = c.lat() != null ? c.lat() : customer.getLatitude();
        Double lng = c.lng() != null ? c.lng() : customer.getLongitude();
        if (lat == null || lng == null) {
            throw ApiException.badRequest("Provide lat and lng, or save your location on your profile first");
        }
        double radius = c.radiusKm() != null ? c.radiusKm()
                : pref != null && pref.getMaxDistanceKm() != null ? pref.getMaxDistanceKm() : DEFAULT_RADIUS_KM;
        String gender = c.gender() != null ? c.gender() : pref != null ? pref.getPreferredGender() : null;
        Double minRating = c.minRating() != null ? c.minRating() : pref != null ? pref.getMinRating() : null;
        Set<Long> languageIds = new HashSet<>();
        if (c.languageId() != null) {
            languageIds.add(c.languageId());
        } else if (pref != null) {
            pref.getPreferredLanguages().forEach(l -> languageIds.add(l.getId()));
        }
        BigDecimal budgetMin = pref == null ? null : pref.getBudgetMin();
        BigDecimal budgetMax = pref == null ? null : pref.getBudgetMax();

        Map<Long, TechnicianProfile> profileByUser = techProfiles.findAll().stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        List<TechnicianResult> out = new ArrayList<>();
        for (UserAccount t : users.findByRoleAndStatusAndLatitudeNotNullAndLongitudeNotNull(Role.TECHNICIAN, VerificationStatus.VERIFIED)) {
            double distance = GeoUtil.distanceKm(lat, lng, t.getLatitude(), t.getLongitude());
            if (distance > radius) continue;
            if (gender != null && !gender.equalsIgnoreCase(t.getGender())) continue;
            if (!languageIds.isEmpty() && t.getLanguages().stream().noneMatch(l -> languageIds.contains(l.getId()))) continue;

            List<TechnicianSkill> relevant = t.getSkills().stream()
                    .filter(s -> c.professionId() == null || s.getProfession().getId().equals(c.professionId()))
                    .toList();
            if (relevant.isEmpty()) continue;
            if ((budgetMin != null || budgetMax != null)
                    && relevant.stream().noneMatch(s -> overlapsBudget(s.getProfession(), budgetMin, budgetMax))) continue;

            TechnicianProfile tp = profileByUser.get(t.getId());
            double avg = tp == null ? 0 : tp.getRatingAverage();
            int count = tp == null ? 0 : tp.getRatingCount();
            // Technicians with no ratings yet are not excluded by a minimum rating (new technicians must be findable).
            if (minRating != null && count > 0 && avg < minRating) continue;

            out.add(new TechnicianResult(t.getId(), t.getFullName(), t.getGender(), views.languages(t.getLanguages()),
                    views.skills(t), tp == null ? null : tp.getQualification(),
                    tp == null ? null : tp.getExperienceYears(), avg, count, count > 0,
                    t.getLatitude(), t.getLongitude(), Math.round(distance * 100.0) / 100.0));
        }
        out.sort(Comparator.comparingDouble(TechnicianResult::distanceKm)
                .thenComparing(Comparator.comparingDouble(TechnicianResult::ratingAverage).reversed()));
        return out;
    }

    static boolean overlapsBudget(Profession p, BigDecimal budgetMin, BigDecimal budgetMax) {
        boolean aboveMin = budgetMin == null || p.getMaxPrice().compareTo(budgetMin) >= 0;
        boolean belowMax = budgetMax == null || p.getMinPrice().compareTo(budgetMax) <= 0;
        return aboveMin && belowMax;
    }

    /** Problem description -> best-fit professions -> nearby technicians for the best fit. */
    public MatchResponse match(UserAccount customer, MatchRequest r) {
        CurrentUser.requireVerified(customer);
        List<MatchingService.ProfessionMatch> matches = matching.match(r.description(), professions.findAll());
        List<ProfessionMatchView> list = matches.stream()
                .map(m -> new ProfessionMatchView(m.profession().getId(), m.profession().getName(), m.score())).toList();
        ProfessionMatchView best = list.isEmpty() ? null : list.get(0);
        List<TechnicianResult> technicians = best == null ? List.of()
                : nearby(customer, new SearchCriteria(r.latitude(), r.longitude(), r.radiusKm(),
                best.professionId(), null, null, null, true));
        return new MatchResponse(list, best, technicians);
    }
}

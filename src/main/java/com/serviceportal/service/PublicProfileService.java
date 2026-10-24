package com.serviceportal.service;

import com.serviceportal.dto.Dtos.*;
import com.serviceportal.model.*;
import com.serviceportal.repository.RatingRepository;
import com.serviceportal.repository.TechnicianProfileRepository;
import com.serviceportal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicProfileService {

    private static final int MAX_REVIEWS = 20;

    private final UserRepository users;
    private final TechnicianProfileRepository techProfiles;
    private final RatingRepository ratings;
    private final Views views;

    public PublicProfileService(UserRepository users, TechnicianProfileRepository techProfiles,
                                RatingRepository ratings, Views views) {
        this.users = users;
        this.techProfiles = techProfiles;
        this.ratings = ratings;
        this.views = views;
    }

    public TechnicianPublicView technician(Long id) {
        UserAccount u = users.findById(id)
                .filter(x -> x.getRole() == Role.TECHNICIAN && x.isVerified())
                .orElseThrow(() -> ApiException.notFound("Technician not found"));
        TechnicianProfile tp = techProfiles.findByUser(u).orElse(null);
        List<ReviewView> reviews = ratings.findByTechnicianOrderByCreatedAtDesc(u).stream().limit(MAX_REVIEWS)
                .map(r -> new ReviewView(r.getScore(), r.getComment(), firstName(r.getCustomer().getFullName()), r.getCreatedAt()))
                .toList();
        return new TechnicianPublicView(u.getId(), u.getFullName(), u.getGender(), views.languages(u.getLanguages()),
                views.skills(u), tp == null ? null : tp.getQualification(), tp == null ? null : tp.getExperienceYears(),
                tp == null ? 0 : tp.getRatingAverage(), tp == null ? 0 : tp.getRatingCount(), reviews);
    }

    /** Reviewers are shown by first name only. */
    static String firstName(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : (n.isEmpty() ? "Customer" : n);
    }
}

package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class CustomerPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private UserAccount user;

    /** Null = any gender. */
    private String preferredGender;
    private Double maxDistanceKm;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private Double minRating;

    @ManyToMany
    @JoinTable(name = "preference_languages",
            joinColumns = @JoinColumn(name = "preference_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id"))
    private Set<Language> preferredLanguages = new HashSet<>();
}

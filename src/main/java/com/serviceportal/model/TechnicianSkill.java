package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class TechnicianSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Profession profession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillLevel skillLevel;

    /** Qualification / experience notes for this profession. */
    @Column(length = 1000)
    private String notes;

    @ManyToOne(optional = false)
    private UserAccount technician;
}

package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class TechnicianProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private UserAccount user;

    @Column(length = 2000)
    private String qualification;

    private Integer experienceYears;

    private double ratingAverage;
    private int ratingCount;
}

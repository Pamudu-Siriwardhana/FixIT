package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private UserAccount customer;

    @ManyToOne(optional = false)
    private UserAccount technician;

    @ManyToOne(optional = false)
    private Profession profession;

    @Column(nullable = false, length = 2000)
    private String description;

    /** Job location (map pin). */
    @Column(nullable = false)
    private Double jobLatitude;
    @Column(nullable = false)
    private Double jobLongitude;
    private String jobAddress;

    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    /** Latest offer on the table and who made it (CUSTOMER or TECHNICIAN). */
    @Column(precision = 12, scale = 2)
    private BigDecimal currentOffer;
    @Enumerated(EnumType.STRING)
    private Role currentOfferBy;

    @Column(precision = 12, scale = 2)
    private BigDecimal agreedPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

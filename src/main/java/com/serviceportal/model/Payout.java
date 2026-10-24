package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Record that the platform paid a technician the balance it collected for a booking (one per booking). */
@Entity
@Getter
@Setter
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Booking booking;

    @ManyToOne(optional = false)
    private UserAccount technician;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Bank transfer reference or similar. */
    @Column(nullable = false, length = 200)
    private String reference;

    @Column(length = 1000)
    private String note;

    @ManyToOne(optional = false)
    private UserAccount paidBy;

    @Column(nullable = false)
    private LocalDateTime paidAt = LocalDateTime.now();
}

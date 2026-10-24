package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A file (NIC photo, certificate...) a user uploads so an admin can verify the profile. */
@Entity
@Getter
@Setter
public class VerificationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private UserAccount user;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    private long size;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}

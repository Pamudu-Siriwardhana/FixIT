package com.serviceportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Admin-managed profession with the platform price range (LKR) used for bidding. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Profession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal maxPrice;

    /** Comma separated words/phrases (any language) used by the keyword matcher. */
    @Column(length = 2000)
    private String keywords;

    public Profession(String name, BigDecimal minPrice, BigDecimal maxPrice, String keywords) {
        this.name = name;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.keywords = keywords;
    }
}

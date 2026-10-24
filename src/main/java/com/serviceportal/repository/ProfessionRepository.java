package com.serviceportal.repository;

import com.serviceportal.model.Profession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfessionRepository extends JpaRepository<Profession, Long> {
    boolean existsByNameIgnoreCase(String name);
    List<Profession> findAllByOrderByNameAsc();
}

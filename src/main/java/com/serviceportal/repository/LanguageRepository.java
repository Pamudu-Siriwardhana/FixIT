package com.serviceportal.repository;

import com.serviceportal.model.Language;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanguageRepository extends JpaRepository<Language, Long> {
    boolean existsByNameIgnoreCase(String name);
    List<Language> findAllByOrderByNameAsc();
}

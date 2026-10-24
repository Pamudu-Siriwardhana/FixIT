package com.serviceportal.repository;

import com.serviceportal.model.Profession;
import com.serviceportal.model.TechnicianSkill;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechnicianSkillRepository extends JpaRepository<TechnicianSkill, Long> {
    Optional<TechnicianSkill> findByTechnicianAndProfession(UserAccount technician, Profession profession);
    boolean existsByTechnicianAndProfession(UserAccount technician, Profession profession);
}

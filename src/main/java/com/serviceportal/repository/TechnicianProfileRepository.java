package com.serviceportal.repository;

import com.serviceportal.model.TechnicianProfile;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {
    Optional<TechnicianProfile> findByUser(UserAccount user);
}

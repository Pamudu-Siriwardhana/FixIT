package com.serviceportal.repository;

import com.serviceportal.model.CustomerPreference;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerPreferenceRepository extends JpaRepository<CustomerPreference, Long> {
    Optional<CustomerPreference> findByUser(UserAccount user);
}

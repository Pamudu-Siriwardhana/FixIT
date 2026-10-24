package com.serviceportal.repository;

import com.serviceportal.model.Role;
import com.serviceportal.model.UserAccount;
import com.serviceportal.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNic(String nic);
    List<UserAccount> findByStatusOrderByIdAsc(VerificationStatus status);
    List<UserAccount> findByRoleAndStatusAndLatitudeNotNullAndLongitudeNotNull(Role role, VerificationStatus status);
}

package com.serviceportal.repository;

import com.serviceportal.model.UserAccount;
import com.serviceportal.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {
    List<VerificationDocument> findByUserOrderByUploadedAtDescIdDesc(UserAccount user);
    long countByUser(UserAccount user);
}

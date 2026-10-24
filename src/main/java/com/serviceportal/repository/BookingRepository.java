package com.serviceportal.repository;

import com.serviceportal.model.Booking;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerOrderByCreatedAtDesc(UserAccount customer);
    List<Booking> findByTechnicianOrderByCreatedAtDesc(UserAccount technician);
}

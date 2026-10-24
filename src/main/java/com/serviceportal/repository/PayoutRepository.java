package com.serviceportal.repository;

import com.serviceportal.model.Booking;
import com.serviceportal.model.Payout;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    boolean existsByBooking(Booking booking);
    List<Payout> findByTechnicianOrderByPaidAtDesc(UserAccount technician);
    List<Payout> findAllByOrderByPaidAtDesc();
}

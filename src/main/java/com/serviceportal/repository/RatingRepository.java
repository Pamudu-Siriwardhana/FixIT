package com.serviceportal.repository;

import com.serviceportal.model.Booking;
import com.serviceportal.model.Rating;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByBooking(Booking booking);
    Optional<Rating> findByBooking(Booking booking);
    List<Rating> findByTechnicianOrderByCreatedAtDesc(UserAccount technician);
}

package com.serviceportal.repository;

import com.serviceportal.model.Booking;
import com.serviceportal.model.Payment;
import com.serviceportal.model.PaymentMethod;
import com.serviceportal.model.PaymentStatus;
import com.serviceportal.model.PaymentType;
import com.serviceportal.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBookingOrderByCreatedAtAscIdAsc(Booking booking);
    List<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);
    List<Payment> findByTypeAndMethodAndStatusOrderByCreatedAtAsc(PaymentType type, PaymentMethod method, PaymentStatus status);

    @Query("select p from Payment p where p.createdAt >= :from and p.createdAt <= :to "
            + "and (p.payer = :user or p.booking.technician = :user) order by p.createdAt desc")
    List<Payment> findForUser(@Param("user") UserAccount user, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select p from Payment p where p.createdAt >= :from and p.createdAt <= :to order by p.createdAt desc")
    List<Payment> findAllBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

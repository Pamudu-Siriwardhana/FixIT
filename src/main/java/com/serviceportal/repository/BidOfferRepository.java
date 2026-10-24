package com.serviceportal.repository;

import com.serviceportal.model.BidOffer;
import com.serviceportal.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidOfferRepository extends JpaRepository<BidOffer, Long> {
    List<BidOffer> findByBookingOrderByCreatedAtAscIdAsc(Booking booking);
}

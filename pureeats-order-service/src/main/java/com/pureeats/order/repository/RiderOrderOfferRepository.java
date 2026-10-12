package com.pureeats.order.repository;

import com.pureeats.order.entity.RiderOrderOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderOrderOfferRepository extends JpaRepository<RiderOrderOffer, Long> {

    List<RiderOrderOffer> findByOrderIdOrderByOfferRoundAscNotifiedAtAsc(Long orderId);
}

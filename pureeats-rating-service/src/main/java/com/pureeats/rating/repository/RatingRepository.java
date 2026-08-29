package com.pureeats.rating.repository;

import com.pureeats.domain.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByOrderId(Integer orderId);

    List<Rating> findByRateableTypeAndRateableId(String rateableType, Long rateableId);
}

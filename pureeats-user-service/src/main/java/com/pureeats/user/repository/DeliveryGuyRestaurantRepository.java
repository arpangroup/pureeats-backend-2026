package com.pureeats.user.repository;

import com.pureeats.domain.entity.DeliveryGuyRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryGuyRestaurantRepository extends JpaRepository<DeliveryGuyRestaurant, Long> {
    List<DeliveryGuyRestaurant> findByDeliveryGuyDetailId(Long deliveryGuyDetailId);

    void deleteByDeliveryGuyDetailId(Long deliveryGuyDetailId);
}

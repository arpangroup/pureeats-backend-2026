package com.pureeats.user.repository;

import com.pureeats.domain.entity.DeliveryGuyDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeliveryGuyDetailRepository extends JpaRepository<DeliveryGuyDetail, Long> {

    @Query("select d from DeliveryGuyDetail d where (:search is null or :search = '' " +
            "or lower(d.name) like lower(concat('%', :search, '%')) or lower(d.vehicleNumber) like lower(concat('%', :search, '%')))")
    Page<DeliveryGuyDetail> findPage(@Param("search") String search, Pageable pageable);

    /** Candidates for RiderInactivityScheduler's auto-offline sweep. */
    List<DeliveryGuyDetail> findByIsOnlineTrue();
}

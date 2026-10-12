package com.pureeats.order.repository;

import com.pureeats.order.entity.RiderOfferRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RiderOfferRunRepository extends JpaRepository<RiderOfferRun, Long> {

    List<RiderOfferRun> findTop30ByOrderByRanAtDesc();

    /** Runs are kept a few days - one a minute adds up. */
    @Modifying
    @Query("delete from RiderOfferRun r where r.ranAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}

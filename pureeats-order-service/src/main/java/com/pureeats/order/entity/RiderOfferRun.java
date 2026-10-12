package com.pureeats.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One run of the re-offer scheduler (RiderReofferScheduler) - for the admin's Partner alerts page. */
@Entity
@Table(name = "rider_offer_runs", indexes = @Index(name = "idx_rider_offer_runs_ran_at", columnList = "ran_at"))
@Getter
@Setter
@NoArgsConstructor
public class RiderOfferRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ran_at", nullable = false)
    private LocalDateTime ranAt;

    /** Orders waiting for a partner when it ran. */
    @Column(name = "waiting_orders", nullable = false)
    private Integer waitingOrders;

    /** Of those, offered again in this run. */
    @Column(name = "reoffered", nullable = false)
    private Integer reoffered;

    /** Partners alerted in this run (all re-offers together). */
    @Column(name = "partners_alerted", nullable = false)
    private Integer partnersAlerted;

    /** Orders whose alerts ran out in this run (admins told). */
    @Column(name = "admin_alerts", nullable = false)
    private Integer adminAlerts;

    /** Comma-separated unique order ids offered again, e.g. "PE-1A2B,PE-3C4D". */
    @Column(name = "reoffered_orders", length = 2000)
    private String reofferedOrders;

    /** False when re-alerts were switched off (the run only recorded that). */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}

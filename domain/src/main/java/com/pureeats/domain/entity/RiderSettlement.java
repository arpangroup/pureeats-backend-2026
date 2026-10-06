package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One settlement between the platform and a delivery partner, covering every trip that was still
 * unsettled at that moment (TripDetail.settlementId points back here). Nets what the rider EARNED
 * (commission, credited to their wallet on delivery) against the cash-on-delivery money they
 * COLLECTED from customers and still hold on the platform's behalf:
 * {@code netAmount = earningsAmount - codAmount}. Positive = the platform paid the rider that much;
 * negative = the rider handed that much cash back to the platform.
 */
@Entity
@Table(name = "rider_settlements")
@Getter
@Setter
@NoArgsConstructor
public class RiderSettlement {

    public static final String DIRECTION_PAID_TO_RIDER = "PAID_TO_RIDER";
    public static final String DIRECTION_COLLECTED_FROM_RIDER = "COLLECTED_FROM_RIDER";
    public static final String DIRECTION_EVEN = "EVEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "rider_user_id", nullable = false)
    private Long riderUserId;

    @Column(name = "earnings_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal earningsAmount;

    @Column(name = "cod_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal codAmount;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    /** Plain string (not an enum column) so Hibernate doesn't emit a CHECK constraint that goes stale. */
    @Column(name = "direction", nullable = false, length = 32)
    private String direction;

    @Column(name = "trip_count", nullable = false)
    private Integer tripCount;

    /** e.g. BANK_TRANSFER / UPI / CASH. */
    @Column(name = "transaction_mode", length = 32)
    private String transactionMode;

    @Column(name = "transaction_reference", length = 128)
    private String transactionReference;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

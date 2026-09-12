package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_gateways")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGateway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Maps this row to what the customer app actually does at checkout - "COD" | "WALLET" | "UPI" (Razorpay-backed once an admin sets a key, see AppConfigService#getRazorpayKeyId). Null on any legacy/decorative row created before this existed - those show in the admin list but the customer app can't act on them. */
    @Column(name = "code")
    private String code;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "logo")
    private String logo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

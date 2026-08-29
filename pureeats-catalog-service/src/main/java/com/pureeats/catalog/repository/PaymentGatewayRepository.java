package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.PaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGatewayRepository extends JpaRepository<PaymentGateway, Long> {
    List<PaymentGateway> findByIsActiveTrue();
}

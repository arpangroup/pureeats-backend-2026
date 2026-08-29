package com.pureeats.user.repository;

import com.pureeats.domain.entity.SmsOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmsOtpRepository extends JpaRepository<SmsOtp, Long> {
    Optional<SmsOtp> findFirstByPhoneOrderByCreatedAtDesc(String phone);
}

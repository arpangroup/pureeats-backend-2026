package com.pureeats.user.repository;

import com.pureeats.domain.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findFirstByUserIdOrderByCreatedAtDesc(Integer userId);
}

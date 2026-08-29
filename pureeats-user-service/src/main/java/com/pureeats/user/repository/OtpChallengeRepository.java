package com.pureeats.user.repository;

import com.pureeats.user.entity.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findByChallengeId(String challengeId);

    /** Used by verify/resend, which mutate attempt/resend counters and must not race each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findWithLockByChallengeId(String challengeId);
}

package com.pureeats.user.repository;

import com.pureeats.user.entity.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByUserIdAndRevokedAtIsNull(Long userId);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}

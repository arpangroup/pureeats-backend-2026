package com.pureeats.notification.repository;

import com.pureeats.domain.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    List<PushToken> findByUserIdAndIsActiveTrue(Integer userId);

    Optional<PushToken> findByToken(String token);
}

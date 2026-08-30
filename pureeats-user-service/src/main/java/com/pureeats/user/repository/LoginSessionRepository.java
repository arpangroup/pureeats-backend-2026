package com.pureeats.user.repository;

import com.pureeats.domain.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    Optional<LoginSession> findFirstByUserIdAndLogoutAtIsNullOrderByLoginAtDesc(Integer userId);
}

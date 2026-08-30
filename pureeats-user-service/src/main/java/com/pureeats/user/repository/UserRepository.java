package com.pureeats.user.repository;

import com.pureeats.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByDeliveryGuyDetailId(Integer deliveryGuyDetailId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}

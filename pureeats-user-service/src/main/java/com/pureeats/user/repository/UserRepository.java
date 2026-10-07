package com.pureeats.user.repository;

import com.pureeats.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Users blocked through the admin switch before it also set the account status (only is_active = INACTIVE)
     * are marked BLOCKED, so lists, badges and the Blocked filter show them as blocked. Deleted/locked rows are left alone.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.accountStatus = com.pureeats.domain.enums.AccountStatus.BLOCKED " +
            "WHERE u.isActive = 'INACTIVE' AND (u.accountStatus IS NULL OR u.accountStatus = com.pureeats.domain.enums.AccountStatus.ACTIVE)")
    int markLegacyBlockedUsers();

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByDeliveryGuyDetailId(Integer deliveryGuyDetailId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByPhone(String phone);
}

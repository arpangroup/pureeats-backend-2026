package com.pureeats.user.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The one place a brand-new {@code User} row gets created for an OTP-based flow (no password) -
 * shared by the legacy phone-OTP login ({@link AuthService#loginWithOtp}) and the new
 * challenge-based login/signup, so "what does a fresh OTP-provisioned account look like" is
 * defined exactly once.
 */
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    /** Placeholder-email domain for phone-only accounts (email is NOT NULL/unique on `users`). */
    public static final String OTP_PLACEHOLDER_EMAIL_DOMAIN = "@otp.pureeats.local";

    private final UserRepository userRepository;
    private final RoleService roleService;

    @Transactional
    public User provisionViaPhoneOtp(String phone, String name) {
        User user = blankUser(name);
        user.setEmail(phone + OTP_PLACEHOLDER_EMAIL_DOMAIN);
        user.setPhone(phone);
        return save(user);
    }

    @Transactional
    public User provisionViaEmail(String email, String name) {
        User user = blankUser(name);
        user.setEmail(email);
        return save(user);
    }

    private User blankUser(String name) {
        User user = new User();
        user.setName(name != null && !name.isBlank() ? name : "PureEats User");
        user.setPassword(null);
        user.setIsActive(User.STATUS_ACTIVE);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private User save(User user) {
        user = userRepository.save(user);
        roleService.assignRole(user.getId(), Role.CUSTOMER);
        return user;
    }
}

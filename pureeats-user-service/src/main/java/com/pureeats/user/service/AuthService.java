package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ConflictException;
import com.pureeats.domain.common.exception.UnauthorizedException;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.dto.*;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserProvisioningService userProvisioningService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        roleService.assertCallerNotPrivileged();
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("An account with this phone number already exists");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setIsActive(STATUS_ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        roleService.assignRole(user.getId(), Role.CUSTOMER);

        return issueToken(user, Role.CUSTOMER);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findByEmailOrPhone(request.emailOrPhone())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        assertActive(user);

        Role role = roleService.resolveRole(user.getId());
        return issueToken(user, role);
    }

    @Transactional
    public OtpSentResponse sendLoginOtp(String phone) {
        userRepository.findByPhone(phone)
                .ifPresent(this::assertActive);
        String otp = otpService.generateAndStore(phone);
        return new OtpSentResponse("OTP sent successfully",
                otpService.isDevMode() ? otp : null);
    }

    @Transactional
    public AuthResponse loginWithOtp(OtpLoginRequest request) {
        otpService.verify(request.phone(), request.otp());

        User user = userRepository.findByPhone(request.phone())
                .orElseGet(() -> userProvisioningService.provisionViaPhoneOtp(request.phone(), request.name()));
        assertActive(user);

        Role role = roleService.resolveRole(user.getId());
        return issueToken(user, role);
    }

    private java.util.Optional<User> findByEmailOrPhone(String emailOrPhone) {
        var byEmail = userRepository.findByEmail(emailOrPhone);
        return byEmail.isPresent() ? byEmail : userRepository.findByPhone(emailOrPhone);
    }

    private void assertActive(User user) {
        if (STATUS_INACTIVE.equalsIgnoreCase(user.getIsActive())) {
            throw new UnauthorizedException("This account has been deactivated");
        }
    }

    private AuthResponse issueToken(User user, Role role) {
        Long deliveryGuyDetailId = (role == Role.DELIVERY && user.getDeliveryGuyDetailId() != null)
                ? user.getDeliveryGuyDetailId().longValue()
                : null;

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(), role, deliveryGuyDetailId);
        String token = jwtTokenProvider.generateToken(principal);

        return AuthResponse.of(token, jwtTokenProvider.getExpirationMs(), UserMapper.toResponse(user, role));
    }
}

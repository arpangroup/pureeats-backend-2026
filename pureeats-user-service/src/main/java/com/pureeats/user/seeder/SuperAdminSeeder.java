package com.pureeats.user.seeder;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * There is exactly one {@code SUPER_ADMIN} account, and it is never created through any API - it
 * is provisioned once, here, on application startup. Idempotent across restarts: if a user already
 * holds the {@code SUPER_ADMIN} role (checked via {@link RoleService#anyUserHasRole}, not by
 * email - config drift on the email must never produce a second super admin), this is a no-op.
 * Otherwise it finds-or-creates the configured account and grants the role.
 * <p>
 * The default password below is a placeholder for local development only - see
 * {@code SUPER_ADMIN_PASSWORD} in the environment-variable reference and change it before any
 * shared/production deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Value("${pureeats.super-admin.name:Super Admin}")
    private String name;

    @Value("${pureeats.super-admin.email:arpangroup1@gmail.com}")
    private String email;

    @Value("${pureeats.super-admin.password:ChangeMe@SuperAdmin123}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roleService.anyUserHasRole(Role.SUPER_ADMIN)) {
            log.debug("A SUPER_ADMIN account already exists - skipping seed");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(this::createUser);
        roleService.assignRole(user.getId(), Role.SUPER_ADMIN);
        log.info("Seeded the SUPER_ADMIN account ({})", PiiMaskUtil.maskEmail(email));
    }

    private User createUser() {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setIsActive(User.STATUS_ACTIVE);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}

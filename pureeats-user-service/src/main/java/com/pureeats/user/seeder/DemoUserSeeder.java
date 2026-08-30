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
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds two demo accounts per non-privileged-adjacent role (ADMIN, STORE_OWNER, EMPLOYEE,
 * DELIVERY, CUSTOMER) on startup, for local/dev testing of every dashboard and role gate without
 * hand-creating accounts through the OTP signup flow. Mirrors {@link SuperAdminSeeder}'s
 * find-or-create-by-email idempotency, but per seed user rather than per role, since (unlike
 * SUPER_ADMIN) it's normal for many real users to hold these roles too.
 * <p>
 * Every seeded account is created with {@code isActive = INACTIVE} - they exist for role/permission
 * testing, not as ready-to-use logins, so an accidental production seed never hands out live access.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class DemoUserSeeder implements ApplicationRunner {

    private record SeedUser(String name, String email, String phone, Role role) {}

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("Demo Admin One", "demo.admin1@pureeats.local", "7000000001", Role.ADMIN),
            new SeedUser("Demo Admin Two", "demo.admin2@pureeats.local", "7000000002", Role.ADMIN),
            new SeedUser("Demo Store Owner One", "demo.owner1@pureeats.local", "7000000003", Role.STORE_OWNER),
            new SeedUser("Demo Store Owner Two", "demo.owner2@pureeats.local", "7000000004", Role.STORE_OWNER),
            new SeedUser("Demo Employee One", "demo.employee1@pureeats.local", "7000000005", Role.EMPLOYEE),
            new SeedUser("Demo Employee Two", "demo.employee2@pureeats.local", "7000000006", Role.EMPLOYEE),
            new SeedUser("Demo Delivery One", "demo.delivery1@pureeats.local", "7000000007", Role.DELIVERY),
            new SeedUser("Demo Delivery Two", "demo.delivery2@pureeats.local", "7000000008", Role.DELIVERY),
            new SeedUser("Demo Customer One", "demo.customer1@pureeats.local", "7000000009", Role.CUSTOMER),
            new SeedUser("Demo Customer Two", "demo.customer2@pureeats.local", "7000000010", Role.CUSTOMER)
    );

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Value("${pureeats.demo-users.password:ChangeMe@Demo123}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedUser seedUser : SEED_USERS) {
            User user = userRepository.findByEmail(seedUser.email()).orElseGet(() -> createUser(seedUser));
            roleService.assignRole(user.getId(), seedUser.role());
        }
        log.info("Seeded {} demo accounts ({})", SEED_USERS.size(),
                SEED_USERS.stream().map(u -> PiiMaskUtil.maskEmail(u.email())).toList());
    }

    private User createUser(SeedUser seedUser) {
        User user = new User();
        user.setName(seedUser.name());
        user.setEmail(seedUser.email());
        user.setPhone(seedUser.phone());
        user.setPassword(passwordEncoder.encode(password));
        user.setIsActive(User.STATUS_INACTIVE);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}

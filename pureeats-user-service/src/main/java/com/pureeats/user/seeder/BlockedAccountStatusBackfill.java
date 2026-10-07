package com.pureeats.user.seeder;

import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup fix-up: accounts blocked from the admin panel before the block switch also set the account status
 * (they only have is_active = INACTIVE) get status BLOCKED, so the Users / Store owners / Delivery partners lists
 * show "Blocked" instead of "Active". Idempotent - does nothing once they're all marked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockedAccountStatusBackfill implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = userRepository.markLegacyBlockedUsers();
        if (updated > 0) log.info("Marked {} previously blocked account(s) with status BLOCKED", updated);
    }
}

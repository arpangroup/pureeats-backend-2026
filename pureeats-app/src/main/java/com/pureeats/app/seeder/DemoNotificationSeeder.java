package com.pureeats.app.seeder;

import com.pureeats.domain.entity.Alert;
import com.pureeats.domain.entity.User;
import com.pureeats.notification.repository.AlertRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** A handful of read/unread in-app alerts per demo account, so the Notifications screen (list, mark-read, delete) has something to show. Runs after DemoUserSeeder; idempotent. */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class DemoNotificationSeeder implements ApplicationRunner {

    private record SeedAlert(String title, String body, boolean isRead, int hoursAgo) {}

    private static final List<SeedAlert> SEED_ALERTS = List.of(
            new SeedAlert("Order delivered", "Your order #PE-DEMO-005 was delivered. Enjoy your meal!", true, 72),
            new SeedAlert("Coupon applied", "WELCOME50 saved you ₹50 on your last order.", true, 50),
            new SeedAlert("New payout settled", "A payout of ₹1,250 was marked paid.", false, 20),
            new SeedAlert("Order out for delivery", "Your order #PE-DEMO-002 is on its way.", false, 3),
            new SeedAlert("Welcome to PureEats", "Thanks for joining! Explore restaurants near you.", true, 240)
    );

    private final UserRepository userRepository;
    private final AlertRepository alertRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedFor("demo.customer1@pureeats.local");
        seedFor("demo.admin1@pureeats.local");
    }

    private void seedFor(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        if (!alertRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(user.getId(), LocalDateTime.now().minusDays(30)).isEmpty()) {
            return;
        }
        for (SeedAlert seed : SEED_ALERTS) {
            Alert alert = new Alert();
            alert.setUserId(user.getId());
            alert.setData("{\"title\":\"" + escape(seed.title()) + "\",\"body\":\"" + escape(seed.body()) + "\"}");
            alert.setIsRead(seed.isRead());
            LocalDateTime createdAt = LocalDateTime.now().minusHours(seed.hoursAgo());
            alert.setCreatedAt(createdAt);
            alert.setUpdatedAt(createdAt);
            alertRepository.save(alert);
        }
        log.info("Seeded {} demo notifications for {}", SEED_ALERTS.size(), email);
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}

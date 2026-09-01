package com.pureeats.user.seeder;

import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link DemoUserSeeder} already creates two DELIVERY-role accounts, but a DELIVERY role alone
 * doesn't give a rider a {@link DeliveryGuyDetail} profile (vehicle, commission rate, rating,
 * online status) - without one they're invisible on the admin Delivery Partners screen, which
 * lists {@code DeliveryGuyDetail} rows, not users. Runs after DemoUserSeeder ({@code @Order(1)})
 * so those accounts already exist; idempotent like every other seeder here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class DemoDeliveryGuySeeder implements ApplicationRunner {

    private record SeedRider(String email, String vehicleNumber, String gender, int age, boolean isOnline, double rating, String photo) {}

    // Stable public placeholder-avatar service (i.pravatar.cc) - same "external dependency for demo
    // data" pattern already used elsewhere here (OpenStreetMap tiles for the map picker). Swap for a
    // real uploaded photo (via MediaAssetService, same as a restaurant cover image) whenever this
    // demo account gets one - MediaUrlResolver already passes an absolute http(s) URL like this
    // through unchanged rather than treating it as a local storage key.
    private static final List<SeedRider> SEED_RIDERS = List.of(
            new SeedRider("demo.delivery1@pureeats.local", "KA01AB1234", "male", 27, true, 4.6, "https://i.pravatar.cc/300?img=12"),
            new SeedRider("demo.delivery2@pureeats.local", "KA05CD5678", "female", 24, false, 4.8, "https://i.pravatar.cc/300?img=45")
    );

    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (SeedRider rider : SEED_RIDERS) {
            User user = userRepository.findByEmail(rider.email()).orElse(null);
            if (user == null) {
                log.warn("Demo rider account {} not found - skipping delivery-guy profile seed", rider.email());
                continue;
            }
            if (user.getDeliveryGuyDetailId() != null) {
                continue;
            }

            DeliveryGuyDetail detail = new DeliveryGuyDetail();
            detail.setName(user.getName());
            detail.setAge(String.valueOf(rider.age()));
            detail.setGender(rider.gender());
            detail.setDescription("Seeded demo delivery partner");
            detail.setVehicleNumber(rider.vehicleNumber());
            detail.setPhoto(rider.photo());
            detail.setCommissionRate(BigDecimal.valueOf(12));
            detail.setMaxAcceptDeliveryLimit(3);
            detail.setRating(BigDecimal.valueOf(rider.rating()));
            detail.setIsActive(true);
            detail.setIsOnline(rider.isOnline());
            detail.setIsNotifiable(true);
            detail.setCreatedAt(LocalDateTime.now());
            detail.setUpdatedAt(LocalDateTime.now());
            detail = deliveryGuyDetailRepository.save(detail);

            user.setDeliveryGuyDetailId(detail.getId().intValue());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            seeded++;
        }
        if (seeded > 0) {
            log.info("Seeded {} demo delivery-partner profile(s)", seeded);
        }
    }
}

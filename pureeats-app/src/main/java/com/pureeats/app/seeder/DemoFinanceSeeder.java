package com.pureeats.app.seeder;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.DeliveryCollection;
import com.pureeats.domain.entity.DeliveryCollectionLog;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantEarning;
import com.pureeats.domain.entity.RestaurantPayout;
import com.pureeats.domain.entity.Transaction;
import com.pureeats.domain.entity.TripDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.entity.Wallet;
import com.pureeats.order.repository.DeliveryCollectionLogRepository;
import com.pureeats.order.repository.DeliveryCollectionRepository;
import com.pureeats.order.repository.RestaurantEarningRepository;
import com.pureeats.order.repository.RestaurantPayoutRepository;
import com.pureeats.order.repository.TransactionRepository;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.order.repository.WalletRepository;
import com.pureeats.user.entity.LoginHistory;
import com.pureeats.user.enums.LoginMethod;
import com.pureeats.user.repository.LoginHistoryRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Demo data for the admin panel's Wallet Transactions, Store Payouts and Delivery Collections
 * screens - none of these get populated by {@link DemoCatalogSeeder}'s directly-inserted orders
 * (they bypass {@code OrderService.placeOrder}, which is what normally records a wallet
 * transaction/restaurant earning). Runs after DemoCatalogSeeder ({@code @Order(2)}); idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class DemoFinanceSeeder implements ApplicationRunner {

    private static final String USER_HOLDER_TYPE = "App\\User";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantEarningRepository restaurantEarningRepository;
    private final RestaurantPayoutRepository restaurantPayoutRepository;
    private final DeliveryCollectionRepository deliveryCollectionRepository;
    private final DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TripDetailRepository tripDetailRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Restaurant> restaurants = restaurantRepository.findAll();
        if (restaurants.isEmpty()) {
            log.warn("No restaurants found - skipping finance demo seed");
            return;
        }
        Optional<User> customer1 = userRepository.findByEmail("demo.customer1@pureeats.local");
        Optional<User> customer2 = userRepository.findByEmail("demo.customer2@pureeats.local");
        Optional<User> delivery1 = userRepository.findByEmail("demo.delivery1@pureeats.local");
        Optional<User> delivery2 = userRepository.findByEmail("demo.delivery2@pureeats.local");

        seedRestaurantPayouts(restaurants.get(0));
        delivery1.ifPresent(this::seedDeliveryCollection);
        customer1.ifPresent(this::seedWalletTransaction);

        delivery1.ifPresent(rider -> seedRiderEarnings(rider, restaurants, customer1.orElse(null)));
        delivery2.ifPresent(rider -> seedRiderEarnings(rider, restaurants, customer2.orElse(null)));
        delivery1.ifPresent(this::seedLoginHistory);
        delivery2.ifPresent(this::seedLoginHistory);
    }

    /** Per-order rider earnings for the delivery-partner detail page's Earnings section - seeded orders never call DeliveryOrderService.creditRiderAndSettle, so no TripDetail rows exist otherwise. */
    private void seedRiderEarnings(User rider, List<Restaurant> restaurants, User customer) {
        if (!tripDetailRepository.findByRiderId(rider.getId().intValue()).isEmpty()) {
            return;
        }
        if (customer == null) {
            return;
        }

        record TripSeed(int daysAgo, BigDecimal orderTotal, BigDecimal distanceKm, String paymentMode) {}
        List<TripSeed> seeds = List.of(
                new TripSeed(1, BigDecimal.valueOf(320), BigDecimal.valueOf(3.4), "ONLINE"),
                new TripSeed(2, BigDecimal.valueOf(180), BigDecimal.valueOf(2.1), "COD"),
                new TripSeed(4, BigDecimal.valueOf(560), BigDecimal.valueOf(5.8), "ONLINE"),
                new TripSeed(6, BigDecimal.valueOf(240), BigDecimal.valueOf(1.9), "COD")
        );

        int orderIdSeed = 90000 + rider.getId().intValue();
        for (TripSeed seed : seeds) {
            Restaurant restaurant = restaurants.get(orderIdSeed % restaurants.size());
            BigDecimal riderEarning = seed.orderTotal().multiply(BigDecimal.valueOf(0.12)).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal restaurantEarning = seed.orderTotal().multiply(BigDecimal.valueOf(0.85)).setScale(2, java.math.RoundingMode.HALF_UP);
            boolean isCod = "COD".equals(seed.paymentMode());

            TripDetail trip = new TripDetail();
            trip.setOrderId(orderIdSeed++);
            trip.setCustomerId(customer.getId().intValue());
            trip.setRestaurantId(restaurant.getId().intValue());
            trip.setRiderId(rider.getId().intValue());
            trip.setDeliveryCollectionId(0);
            trip.setDistanceTravelled(seed.distanceKm());
            trip.setRiderEarning(riderEarning);
            trip.setRestaurantEarning(restaurantEarning);
            trip.setCashCollectedFromCustomer(isCod ? seed.orderTotal() : BigDecimal.ZERO);
            trip.setCashOnHold(BigDecimal.ZERO);
            trip.setIsSettlementDone(1);
            trip.setCreatedAt(LocalDateTime.now().minusDays(seed.daysAgo()));
            trip.setUpdatedAt(LocalDateTime.now().minusDays(seed.daysAgo()));
            tripDetailRepository.save(trip);
        }

        log.info("Seeded {} demo trip(s)/earning(s) for rider #{}", seeds.size(), rider.getId());
    }

    /** Last-5-logins activity feed for the delivery-partner detail page. */
    private void seedLoginHistory(User user) {
        if (loginHistoryRepository.findByUserId(user.getId(), PageRequest.of(0, 1)).hasContent()) {
            return;
        }

        record LoginSeed(int hoursAgo, boolean success, String city, String region) {}
        List<LoginSeed> seeds = List.of(
                new LoginSeed(2, true, "Bengaluru", "Karnataka"),
                new LoginSeed(26, true, "Bengaluru", "Karnataka"),
                new LoginSeed(50, false, "Bengaluru", "Karnataka"),
                new LoginSeed(74, true, "Bengaluru", "Karnataka"),
                new LoginSeed(120, true, "Mysuru", "Karnataka")
        );
        for (LoginSeed seed : seeds) {
            LoginHistory entry = new LoginHistory();
            entry.setUserId(user.getId());
            entry.setLoginMethod(LoginMethod.PHONE_OTP);
            entry.setStatus(seed.success() ? "SUCCESS" : "FAILED");
            entry.setIpAddress("10.0.0." + (10 + seed.hoursAgo() % 240));
            entry.setDeviceId("demo-device-" + user.getId());
            entry.setUserAgent("PureEats Rider App/1.0 (Android 14)");
            entry.setCountry("India");
            entry.setRegion(seed.region());
            entry.setCity(seed.city());
            entry.setLatitude(12.9716);
            entry.setLongitude(77.5946);
            entry.setOccurredAt(LocalDateTime.now().minusHours(seed.hoursAgo()));
            if (!seed.success()) {
                entry.setFailureReason("Incorrect OTP");
            }
            loginHistoryRepository.save(entry);
        }

        log.info("Seeded {} demo login-history entries for user #{}", seeds.size(), user.getId());
    }

    private void seedRestaurantPayouts(Restaurant restaurant) {
        if (!restaurantEarningRepository.findByRestaurantIdAndIsProcessedFalse(restaurant.getId().intValue()).isEmpty()
                || !restaurantPayoutRepository.findAll().isEmpty()) {
            return;
        }

        RestaurantEarning settledEarning = newEarning(restaurant.getId().intValue(), BigDecimal.valueOf(1250));
        settledEarning.setIsRequested(true);
        settledEarning = restaurantEarningRepository.save(settledEarning);

        RestaurantPayout paidPayout = new RestaurantPayout();
        paidPayout.setRestaurantId(restaurant.getId().intValue());
        paidPayout.setRestaurantEarningId(settledEarning.getId().intValue());
        paidPayout.setAmount(settledEarning.getAmount());
        paidPayout.setStatus("PAID");
        paidPayout.setTransactionMode("BANK_TRANSFER");
        paidPayout.setTransactionId("TXN-DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        paidPayout.setMessage("Seeded demo payout - settled");
        paidPayout.setCreatedAt(LocalDateTime.now().minusDays(5));
        paidPayout.setUpdatedAt(LocalDateTime.now().minusDays(4));
        paidPayout = restaurantPayoutRepository.save(paidPayout);

        settledEarning.setIsProcessed(true);
        settledEarning.setRestaurantPayoutId(paidPayout.getId().intValue());
        restaurantEarningRepository.save(settledEarning);

        RestaurantEarning pendingEarning = newEarning(restaurant.getId().intValue(), BigDecimal.valueOf(430));
        pendingEarning.setIsRequested(true);
        pendingEarning = restaurantEarningRepository.save(pendingEarning);

        RestaurantPayout pendingPayout = new RestaurantPayout();
        pendingPayout.setRestaurantId(restaurant.getId().intValue());
        pendingPayout.setRestaurantEarningId(pendingEarning.getId().intValue());
        pendingPayout.setAmount(pendingEarning.getAmount());
        pendingPayout.setStatus("PENDING");
        pendingPayout.setMessage("Seeded demo payout - awaiting settlement");
        pendingPayout.setCreatedAt(LocalDateTime.now().minusHours(6));
        pendingPayout.setUpdatedAt(LocalDateTime.now().minusHours(6));
        pendingPayout = restaurantPayoutRepository.save(pendingPayout);

        pendingEarning.setRestaurantPayoutId(pendingPayout.getId().intValue());
        restaurantEarningRepository.save(pendingEarning);

        log.info("Seeded demo restaurant payouts for restaurant #{}", restaurant.getId());
    }

    private RestaurantEarning newEarning(Integer restaurantId, BigDecimal amount) {
        RestaurantEarning earning = new RestaurantEarning();
        earning.setRestaurantId(restaurantId);
        earning.setAmount(amount);
        earning.setIsRequested(false);
        earning.setIsProcessed(false);
        earning.setCreatedAt(LocalDateTime.now().minusDays(6));
        earning.setUpdatedAt(LocalDateTime.now().minusDays(6));
        return earning;
    }

    private void seedDeliveryCollection(User rider) {
        if (deliveryCollectionRepository.findByUserId(rider.getId().intValue()).isPresent()) {
            return;
        }

        DeliveryCollection collection = new DeliveryCollection();
        collection.setUserId(rider.getId().intValue());
        collection.setAmount(BigDecimal.valueOf(340));
        collection.setCreatedAt(LocalDateTime.now().minusDays(3));
        collection.setUpdatedAt(LocalDateTime.now().minusHours(2));
        collection = deliveryCollectionRepository.save(collection);

        record LogSeed(BigDecimal amount, String type, String message, int hoursAgo) {}
        List<LogSeed> logSeeds = List.of(
                new LogSeed(BigDecimal.valueOf(220), "credit", "Cash collected for order #PE-DEMO-004", 60),
                new LogSeed(BigDecimal.valueOf(180), "credit", "Cash collected for order #PE-DEMO-012", 30),
                new LogSeed(BigDecimal.valueOf(60), "debit", "Handed over to store during shift end", 2)
        );
        for (LogSeed seed : logSeeds) {
            DeliveryCollectionLog logEntry = new DeliveryCollectionLog();
            logEntry.setDeliveryCollectionId(collection.getId().intValue());
            logEntry.setAmount(seed.amount());
            logEntry.setType(seed.type());
            logEntry.setMessage(seed.message());
            logEntry.setCreatedAt(LocalDateTime.now().minusHours(seed.hoursAgo()));
            logEntry.setUpdatedAt(LocalDateTime.now().minusHours(seed.hoursAgo()));
            deliveryCollectionLogRepository.save(logEntry);
        }

        log.info("Seeded demo delivery collection for rider #{}", rider.getId());
    }

    private void seedWalletTransaction(User customer) {
        Wallet wallet = walletRepository.findByHolderTypeAndHolderId(USER_HOLDER_TYPE, customer.getId())
                .orElseGet(() -> {
                    Wallet w = new Wallet();
                    w.setHolderType(USER_HOLDER_TYPE);
                    w.setHolderId(customer.getId());
                    w.setName("default");
                    w.setSlug("default");
                    w.setBalance(0L);
                    w.setDecimalPlaces((short) 2);
                    w.setCreatedAt(LocalDateTime.now());
                    w.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(w);
                });

        if (!transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).isEmpty()) {
            return;
        }

        long amountMinorUnits = 5000L;
        wallet.setBalance(wallet.getBalance() + amountMinorUnits);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        Transaction transaction = new Transaction();
        transaction.setPayableType(USER_HOLDER_TYPE);
        transaction.setPayableId(customer.getId());
        transaction.setWalletId(wallet.getId());
        transaction.setType("deposit");
        transaction.setAmount(amountMinorUnits);
        transaction.setConfirmed(true);
        transaction.setMeta("Refund for cancelled order #PE-DEMO-008");
        transaction.setUuid(UUID.randomUUID().toString());
        transaction.setCreatedAt(LocalDateTime.now().minusDays(1));
        transaction.setUpdatedAt(LocalDateTime.now().minusDays(1));
        transactionRepository.save(transaction);

        log.info("Seeded demo wallet transaction for user #{}", customer.getId());
    }
}

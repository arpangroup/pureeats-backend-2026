package com.pureeats.app.seeder;

import com.pureeats.catalog.repository.AddonCategoryRepository;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.catalog.repository.CouponRepository;
import com.pureeats.catalog.repository.ItemCategoryRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantCategoryRepository;
import com.pureeats.catalog.repository.RestaurantCategoryRestaurantRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.repository.RestaurantUserRepository;
import com.pureeats.domain.entity.Addon;
import com.pureeats.domain.entity.AddonCategory;
import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.ItemCategory;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.OrderItem;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantCategory;
import com.pureeats.domain.entity.RestaurantCategoryRestaurant;
import com.pureeats.domain.entity.RestaurantUser;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.DiscountType;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.domain.enums.PaymentMode;
import com.pureeats.order.repository.OrderItemRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds demo catalog + order data for the admin panel (Stores/Items/Addons/Coupons/Orders screens)
 * so those screens have something to list in {@code uat}/live mode: 2 restaurant categories, 10
 * restaurants split across the two demo store owners {@link com.pureeats.user.seeder.DemoUserSeeder}
 * seeds, a handful of item/addon categories + 10 items + several addons, 2 coupons, and ~20 orders
 * against the two demo customers. Runs after {@code DemoUserSeeder} ({@code @Order(1)}) so the
 * demo owner/customer accounts it references already exist; if they don't (e.g. seeding disabled
 * upstream), this logs a warning and skips rather than failing startup.
 * <p>
 * Idempotent: every entity is found-or-created by a natural key (name/code/uniqueOrderId), same
 * pattern as {@code DemoUserSeeder} - safe to leave running on every restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(2)
public class DemoCatalogSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    private final RestaurantCategoryRepository restaurantCategoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRestaurantRepository restaurantCategoryRestaurantRepository;
    private final RestaurantUserRepository restaurantUserRepository;

    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;

    private final AddonCategoryRepository addonCategoryRepository;
    private final AddonRepository addonRepository;

    private final CouponRepository couponRepository;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<User> owner1 = userRepository.findByEmail("demo.owner1@pureeats.local");
        Optional<User> owner2 = userRepository.findByEmail("demo.owner2@pureeats.local");
        Optional<User> customer1 = userRepository.findByEmail("demo.customer1@pureeats.local");
        Optional<User> customer2 = userRepository.findByEmail("demo.customer2@pureeats.local");

        if (owner1.isEmpty() || owner2.isEmpty() || customer1.isEmpty() || customer2.isEmpty()) {
            log.warn("Demo owner/customer accounts not found - skipping catalog/order seeding (DemoUserSeeder may not have run)");
            return;
        }

        List<RestaurantCategory> categories = seedRestaurantCategories();
        List<Restaurant> restaurants = seedRestaurants(owner1.get(), owner2.get(), categories);
        List<ItemCategory> itemCategories = seedItemCategories(owner1.get());
        List<Item> items = seedItems(restaurants, itemCategories);
        List<AddonCategory> addonCategories = seedAddonCategories(owner1.get());
        seedAddons(owner1.get(), addonCategories);
        seedCoupons(restaurants);
        seedOrders(restaurants, items, List.of(customer1.get(), customer2.get()));

        log.info("Demo catalog seed complete: {} categories, {} restaurants, {} items, {} coupons",
                categories.size(), restaurants.size(), items.size(), 2);
    }

    private List<RestaurantCategory> seedRestaurantCategories() {
        return List.of("North Indian", "Chinese").stream().map(name ->
                restaurantCategoryRepository.findAll().stream().filter(c -> name.equals(c.getName())).findFirst()
                        .orElseGet(() -> {
                            RestaurantCategory category = new RestaurantCategory();
                            category.setName(name);
                            category.setIsActive(true);
                            category.setCreatedAt(LocalDateTime.now());
                            category.setUpdatedAt(LocalDateTime.now());
                            return restaurantCategoryRepository.save(category);
                        })
        ).toList();
    }

    private List<Restaurant> seedRestaurants(User owner1, User owner2, List<RestaurantCategory> categories) {
        List<Restaurant> restaurants = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String name = "Demo Restaurant " + i;
            final int idx = i;
            Restaurant restaurant = restaurantRepository.findAll().stream()
                    .filter(r -> name.equals(r.getName())).findFirst()
                    .orElseGet(() -> {
                        Restaurant r = new Restaurant();
                        r.setName(name);
                        r.setDescription("Seeded demo restaurant #" + idx);
                        r.setContactNumber("98765" + String.format("%05d", 10000 + idx));
                        r.setOpeningTime(LocalTime.of(9, 0));
                        r.setClosingTime(LocalTime.of(22, 0));
                        r.setAddress("123 Demo Street, Bengaluru");
                        r.setPincode("560001");
                        r.setLandmark("Near Demo Circle");
                        r.setLatitude("12.9716");
                        r.setLongitude("77.5946");
                        r.setIsPureveg(false);
                        r.setSlug(slugify(name) + "-" + idx);
                        r.setSku("DEMO-SKU-" + idx);
                        r.setIsActive(true);
                        r.setIsAccepted(true);
                        r.setIsFeatured(false);
                        r.setCommissionRate(BigDecimal.TEN);
                        r.setRestaurantCharges(BigDecimal.valueOf(10));
                        r.setDeliveryCharges(BigDecimal.valueOf(30));
                        r.setDeliveryType(0);
                        r.setDeliveryChargeType("FIXED");
                        r.setDeliveryRadius(BigDecimal.valueOf(5));
                        r.setMinOrderPrice(BigDecimal.valueOf(99));
                        r.setIsNotifiable(true);
                        r.setAutoAcceptable(false);
                        r.setIsSchedulable(false);
                        r.setIsAcceptCod(true);
                        r.setCreatedAt(LocalDateTime.now());
                        r.setUpdatedAt(LocalDateTime.now());
                        return restaurantRepository.save(r);
                    });
            restaurants.add(restaurant);

            User owner = i % 2 == 0 ? owner2 : owner1;
            if (restaurantUserRepository.findByUserIdAndRestaurantId(owner.getId(), restaurant.getId()).isEmpty()) {
                RestaurantUser link = new RestaurantUser();
                link.setUserId(owner.getId());
                link.setRestaurantId(restaurant.getId());
                link.setCreatedAt(LocalDateTime.now());
                link.setUpdatedAt(LocalDateTime.now());
                restaurantUserRepository.save(link);
            }

            RestaurantCategory category = categories.get(i % categories.size());
            if (restaurantCategoryRestaurantRepository.findByRestaurantCategoryId(category.getId()).stream()
                    .noneMatch(link -> link.getRestaurantId().equals(restaurant.getId()))) {
                RestaurantCategoryRestaurant link = new RestaurantCategoryRestaurant();
                link.setRestaurantCategoryId(category.getId());
                link.setRestaurantId(restaurant.getId());
                link.setCreatedAt(LocalDateTime.now());
                link.setUpdatedAt(LocalDateTime.now());
                restaurantCategoryRestaurantRepository.save(link);
            }
        }
        return restaurants;
    }

    private List<ItemCategory> seedItemCategories(User owner) {
        return List.of("Starters", "Main Course", "Beverages").stream().map(name ->
                itemCategoryRepository.findByUserId(owner.getId().intValue()).stream()
                        .filter(c -> name.equals(c.getName())).findFirst()
                        .orElseGet(() -> {
                            ItemCategory category = new ItemCategory();
                            category.setName(name);
                            category.setUserId(owner.getId().intValue());
                            category.setIsEnabled(true);
                            category.setCreatedAt(LocalDateTime.now());
                            category.setUpdatedAt(LocalDateTime.now());
                            return itemCategoryRepository.save(category);
                        })
        ).toList();
    }

    private List<Item> seedItems(List<Restaurant> restaurants, List<ItemCategory> itemCategories) {
        List<Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < restaurants.size(); i++) {
            Restaurant restaurant = restaurants.get(i);
            String name = "Demo Item " + (i + 1);
            final int idx = i;
            Item item = itemRepository.findByRestaurantId(restaurant.getId().intValue()).stream()
                    .filter(it -> name.equals(it.getName())).findFirst()
                    .orElseGet(() -> {
                        Item it = new Item();
                        it.setRestaurantId(restaurant.getId().intValue());
                        it.setItemCategoryId(itemCategories.get(idx % itemCategories.size()).getId().intValue());
                        it.setName(name);
                        it.setPrice(BigDecimal.valueOf(150 + idx * 10));
                        it.setOldPrice(BigDecimal.ZERO);
                        it.setDesc("Seeded demo menu item");
                        it.setIsRecommended(idx % 3 == 0);
                        it.setIsPopular(idx % 2 == 0);
                        it.setIsNew(true);
                        it.setIsActive(true);
                        it.setIsVeg(idx % 2 == 0);
                        it.setCreatedAt(LocalDateTime.now());
                        it.setUpdatedAt(LocalDateTime.now());
                        return itemRepository.save(it);
                    });
            items.add(item);
        }
        return items;
    }

    private List<AddonCategory> seedAddonCategories(User owner) {
        return List.of("Size", "Extra Toppings").stream().map(name ->
                addonCategoryRepository.findByUserId(owner.getId().intValue()).stream()
                        .filter(c -> name.equals(c.getName())).findFirst()
                        .orElseGet(() -> {
                            AddonCategory category = new AddonCategory();
                            category.setName(name);
                            category.setType(name.equals("Size") ? "single" : "multiple");
                            category.setUserId(owner.getId().intValue());
                            category.setCreatedAt(LocalDateTime.now());
                            category.setUpdatedAt(LocalDateTime.now());
                            return addonCategoryRepository.save(category);
                        })
        ).toList();
    }

    private void seedAddons(User owner, List<AddonCategory> addonCategories) {
        record Seed(String name, BigDecimal price, int categoryIndex) {}
        List<Seed> seeds = List.of(
                new Seed("Regular", BigDecimal.ZERO, 0),
                new Seed("Large", BigDecimal.valueOf(60), 0),
                new Seed("Extra Cheese", BigDecimal.valueOf(40), 1),
                new Seed("Extra Sauce", BigDecimal.valueOf(20), 1)
        );
        for (Seed seed : seeds) {
            AddonCategory category = addonCategories.get(seed.categoryIndex());
            boolean exists = addonRepository.findByAddonCategoryIdAndIsActiveTrue(category.getId().intValue()).stream()
                    .anyMatch(a -> seed.name().equals(a.getName()));
            if (!exists) {
                Addon addon = new Addon();
                addon.setName(seed.name());
                addon.setPrice(seed.price());
                addon.setAddonCategoryId(category.getId().intValue());
                addon.setUserId(owner.getId().intValue());
                addon.setIsActive(true);
                addon.setCreatedAt(LocalDateTime.now());
                addon.setUpdatedAt(LocalDateTime.now());
                addonRepository.save(addon);
            }
        }
    }

    private void seedCoupons(List<Restaurant> restaurants) {
        record Seed(String code, String name, DiscountType type, String discount, String upto, BigDecimal minOrder, Integer restaurantId) {}
        List<Seed> seeds = List.of(
                new Seed("WELCOME50", "Welcome Offer", DiscountType.AMOUNT, "50", "50", BigDecimal.valueOf(199), 0),
                new Seed("SAVE10", "10% Off", DiscountType.PERCENTAGE, "10", "100", BigDecimal.valueOf(299),
                        restaurants.get(0).getId().intValue())
        );
        for (Seed seed : seeds) {
            if (couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(seed.code()).isPresent()) {
                continue;
            }
            Coupon coupon = new Coupon();
            coupon.setName(seed.name());
            coupon.setDescription("Seeded demo coupon");
            coupon.setCode(seed.code());
            coupon.setDiscountType(seed.type().name());
            coupon.setDiscount(seed.discount());
            coupon.setUptoAmount(seed.upto());
            coupon.setMinOrderAmount(seed.minOrder());
            coupon.setExpiryDate(LocalDateTime.now().plusDays(90));
            coupon.setRestaurantId(seed.restaurantId());
            coupon.setIsActive(true);
            coupon.setTotalCoupon(100);
            coupon.setCount(0);
            coupon.setMaxCount(100);
            coupon.setCreatedAt(LocalDateTime.now());
            coupon.setUpdatedAt(LocalDateTime.now());
            couponRepository.save(coupon);
        }
    }

    private void seedOrders(List<Restaurant> restaurants, List<Item> items, List<User> customers) {
        OrderStatusCode[] statusCycle = {
                OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.READY_FOR_PICKUP,
                OrderStatusCode.PICKED_UP, OrderStatusCode.DELIVERED, OrderStatusCode.DELIVERED,
                OrderStatusCode.DELIVERED, OrderStatusCode.CANCELLED,
        };
        for (int i = 0; i < 20; i++) {
            String uniqueOrderId = "PE-DEMO-" + String.format("%03d", i + 1);
            if (orderRepository.findAll().stream().anyMatch(o -> uniqueOrderId.equals(o.getUniqueOrderId()))) {
                continue;
            }

            Restaurant restaurant = restaurants.get(i % restaurants.size());
            Item item = items.get(i % items.size());
            User customer = customers.get(i % customers.size());

            BigDecimal itemTotal = item.getPrice();
            BigDecimal tax = itemTotal.multiply(BigDecimal.valueOf(0.05)).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal deliveryCharge = BigDecimal.valueOf(30);
            BigDecimal payable = itemTotal.add(tax).add(deliveryCharge);

            Order order = new Order();
            order.setUniqueOrderId(uniqueOrderId);
            order.setOrderstatusId(orderStatusService.idFor(statusCycle[i % statusCycle.length]));
            order.setUserId(customer.getId().intValue());
            order.setRestaurantId(restaurant.getId().intValue());
            order.setAddress("456 Demo Avenue, Bengaluru");
            order.setLocation("{\"latitude\":\"12.9716\",\"longitude\":\"77.5946\"}");
            order.setTax(tax);
            order.setRestaurantCharge(BigDecimal.valueOf(10));
            order.setDeliveryCharge(deliveryCharge);
            order.setDriverTipAmount(BigDecimal.ZERO);
            order.setTotal(itemTotal);
            order.setPayable(payable);
            order.setPaymentMode(i % 2 == 0 ? PaymentMode.COD.name() : PaymentMode.WALLET.name());
            order.setDeliveryPin(String.valueOf(1000 + i));
            order.setDeliveryType(0);
            order.setOrderFrom("SEED");
            order.setCreatedAt(LocalDateTime.now().minusDays(20 - i));
            order.setUpdatedAt(LocalDateTime.now().minusDays(20 - i));
            order = orderRepository.save(order);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId().intValue());
            orderItem.setItemId(item.getId().intValue());
            orderItem.setName(item.getName());
            orderItem.setQuantity(1);
            orderItem.setPrice(item.getPrice());
            orderItem.setCreatedAt(order.getCreatedAt());
            orderItem.setUpdatedAt(order.getCreatedAt());
            orderItemRepository.save(orderItem);
        }
    }

    private static String slugify(String name) {
        return name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}

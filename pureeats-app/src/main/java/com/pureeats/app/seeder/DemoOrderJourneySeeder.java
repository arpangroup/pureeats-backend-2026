package com.pureeats.app.seeder;

import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.AcceptDelivery;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.OrderItem;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.domain.enums.PaymentMode;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderItemRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds one demo order per {@link OrderStatusCode} (plus a couple of self-pickup variants) so the
 * admin panel's order list and its "Order flow" journey diagram have a real, walkable example of
 * every state - not just the handful {@link DemoCatalogSeeder} cycles through. Runs after it
 * ({@code @Order(3)}, vs {@code @Order(2)}) so the restaurants/items/customers/riders it (and
 * {@code DemoUserSeeder}) seed already exist; skips with a warning if they don't.
 * <p>
 * Idempotent by {@code uniqueOrderId}, same as {@link DemoCatalogSeeder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(3)
public class DemoOrderJourneySeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusLogRepository orderStatusLogRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final OrderStatusService orderStatusService;

    /** One declarative row per demo order - the full journey is derived from {@code status} via {@link #pathFor}. */
    private record Seed(String suffix, OrderStatusCode status, boolean selfPickup, PaymentMode paymentMode,
                         boolean withRider, boolean riderCompleted, String note) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("PLACED", OrderStatusCode.PLACED, false, PaymentMode.RAZORPAY, false, false,
                    "Just placed - awaiting restaurant response"),
            new Seed("ACCEPTED", OrderStatusCode.RESTAURANT_ACCEPTED, false, PaymentMode.COD, false, false,
                    "Restaurant confirmed, kitchen not started yet"),
            new Seed("PREPARING", OrderStatusCode.PREPARING, false, PaymentMode.RAZORPAY, false, false,
                    "Kitchen actively cooking"),
            new Seed("READY", OrderStatusCode.READY_FOR_PICKUP, false, PaymentMode.COD, false, false,
                    "Packed and waiting - no rider matched yet"),
            new Seed("ASSIGNED", OrderStatusCode.RIDER_ASSIGNED, false, PaymentMode.COD, true, false,
                    "Rider matched, heading to the restaurant"),
            new Seed("PICKEDUP", OrderStatusCode.PICKED_UP, false, PaymentMode.RAZORPAY, true, false,
                    "Rider collected the order from the restaurant"),
            new Seed("ONTHEWAY", OrderStatusCode.ON_THE_WAY, false, PaymentMode.COD, true, false,
                    "Rider en route to the customer"),
            new Seed("DELIVERED", OrderStatusCode.DELIVERED, false, PaymentMode.RAZORPAY, true, true,
                    "Delivered successfully"),
            new Seed("CANCELLED", OrderStatusCode.CANCELLED, false, PaymentMode.WALLET, false, false,
                    "Customer cancelled after the restaurant accepted"),
            new Seed("REJECTED", OrderStatusCode.REJECTED, false, PaymentMode.COD, false, false,
                    "Restaurant declined the order outright"),
            new Seed("RETURNED", OrderStatusCode.RETURNED, false, PaymentMode.RAZORPAY, true, false,
                    "Rider couldn't complete the handoff - refund initiated"),
            new Seed("AUTOCANCEL", OrderStatusCode.AUTO_CANCELLED, false, PaymentMode.COD, false, false,
                    "No rider found in time - system cancelled"),
            new Seed("PICKUPWAIT", OrderStatusCode.READY_FOR_PICKUP, true, PaymentMode.COD, false, false,
                    "Self-pickup order - ready for the customer to collect"),
            new Seed("PICKUPDONE", OrderStatusCode.SELF_PICKUP_COMPLETED, true, PaymentMode.RAZORPAY, false, false,
                    "Self-pickup order - collected by the customer")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<User> customer1 = userRepository.findByEmail("demo.customer1@pureeats.local");
        Optional<User> customer2 = userRepository.findByEmail("demo.customer2@pureeats.local");
        Optional<User> rider1 = userRepository.findByEmail("demo.delivery1@pureeats.local");
        Optional<User> rider2 = userRepository.findByEmail("demo.delivery2@pureeats.local");
        List<Restaurant> restaurants = restaurantRepository.findAll().stream()
                .filter(r -> r.getName() != null && r.getName().startsWith("Demo Restaurant")).toList();

        if (customer1.isEmpty() || customer2.isEmpty() || rider1.isEmpty() || rider2.isEmpty() || restaurants.isEmpty()) {
            log.warn("Demo customers/riders/restaurants not found - skipping order-journey seeding "
                    + "(DemoUserSeeder/DemoCatalogSeeder may not have run yet)");
            return;
        }
        List<User> customers = List.of(customer1.get(), customer2.get());
        List<User> riders = List.of(rider1.get(), rider2.get());

        int created = 0;
        for (int i = 0; i < SEEDS.size(); i++) {
            if (seedOrder(SEEDS.get(i), i, restaurants, customers, riders)) {
                created++;
            }
        }
        log.info("Demo order-journey seed complete: {} new orders (of {} total states demonstrated)", created, SEEDS.size());
    }

    private boolean seedOrder(Seed seed, int index, List<Restaurant> restaurants, List<User> customers, List<User> riders) {
        String uniqueOrderId = "PE-JOURNEY-" + seed.suffix();
        if (orderRepository.findAll().stream().anyMatch(o -> uniqueOrderId.equals(o.getUniqueOrderId()))) {
            return false;
        }

        Restaurant restaurant = restaurants.get(index % restaurants.size());
        Item item = itemRepository.findByRestaurantId(restaurant.getId().intValue()).stream().findFirst().orElse(null);
        User customer = customers.get(index % customers.size());
        User rider = riders.get(index % riders.size());

        BigDecimal itemPrice = item != null ? item.getPrice() : BigDecimal.valueOf(199);
        int quantity = 1 + (index % 3);
        BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal tax = itemTotal.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal restaurantCharge = BigDecimal.valueOf(10);
        BigDecimal deliveryCharge = seed.selfPickup() ? BigDecimal.ZERO : BigDecimal.valueOf(30);
        BigDecimal driverTip = seed.withRider() && index % 2 == 0 ? BigDecimal.valueOf(15) : BigDecimal.ZERO;
        BigDecimal payable = itemTotal.add(tax).add(restaurantCharge).add(deliveryCharge).add(driverTip);

        // Spread creation times over the last few hours so "placed X ago" reads naturally and the
        // journey timestamps (created_at + N minutes per step, below) all land in the past.
        LocalDateTime createdAt = LocalDateTime.now().minusHours(SEEDS.size() - index).minusMinutes(index * 7L);

        Order order = new Order();
        order.setUniqueOrderId(uniqueOrderId);
        order.setOrderstatusId(orderStatusService.idFor(seed.status()));
        order.setUserId(customer.getId().intValue());
        order.setRestaurantId(restaurant.getId().intValue());
        order.setAddress(seed.selfPickup() ? restaurant.getAddress() : "742 Demo Layout, Bengaluru");
        order.setLocation("{\"latitude\":\"12.9352\",\"longitude\":\"77.6146\"}");
        order.setTax(tax);
        order.setRestaurantCharge(restaurantCharge);
        order.setDeliveryCharge(deliveryCharge);
        order.setDriverTipAmount(driverTip);
        order.setTotal(itemTotal);
        order.setPayable(payable);
        order.setPaymentMode(seed.paymentMode().name());
        order.setDeliveryPin(String.valueOf(4000 + index));
        order.setDeliveryType(seed.selfPickup() ? 1 : 0);
        order.setPrepareTime(20);
        order.setOrderFrom("SEED");
        order.setOrderComment(seed.note());
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt.plusMinutes(pathFor(seed.status()).size() * 8L));
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId().intValue());
        orderItem.setItemId(item != null ? item.getId().intValue() : 0);
        orderItem.setName(item != null ? item.getName() : "Demo Item");
        orderItem.setQuantity(quantity);
        orderItem.setPrice(itemPrice);
        orderItem.setCreatedAt(order.getCreatedAt());
        orderItem.setUpdatedAt(order.getCreatedAt());
        orderItemRepository.save(orderItem);

        if (seed.withRider()) {
            AcceptDelivery accept = new AcceptDelivery();
            accept.setOrderId(order.getId().intValue());
            accept.setUserId(rider.getId().intValue());
            accept.setCustomerId(customer.getId().intValue());
            accept.setIsComplete(seed.riderCompleted());
            acceptDeliveryRepository.save(accept);
        }

        backfillJourney(order, seed.status(), customer.getId().longValue(), seed.withRider() ? rider.getId().longValue() : null);
        return true;
    }

    /** The full sequence of statuses a plausible order passes through on its way to {@code finalStatus}. */
    private static List<OrderStatusCode> pathFor(OrderStatusCode finalStatus) {
        List<OrderStatusCode> happyPath = List.of(
                OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.PREPARING,
                OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.RIDER_ASSIGNED, OrderStatusCode.PICKED_UP,
                OrderStatusCode.ON_THE_WAY, OrderStatusCode.DELIVERED);
        int idx = happyPath.indexOf(finalStatus);
        if (idx >= 0) {
            return happyPath.subList(0, idx + 1);
        }
        return switch (finalStatus) {
            case SELF_PICKUP_COMPLETED -> List.of(OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED,
                    OrderStatusCode.PREPARING, OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.SELF_PICKUP_COMPLETED);
            case CANCELLED -> List.of(OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.CANCELLED);
            case REJECTED -> List.of(OrderStatusCode.PLACED, OrderStatusCode.REJECTED);
            case RETURNED -> List.of(OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.PREPARING,
                    OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.RIDER_ASSIGNED, OrderStatusCode.PICKED_UP,
                    OrderStatusCode.ON_THE_WAY, OrderStatusCode.RETURNED);
            case AUTO_CANCELLED -> List.of(OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED,
                    OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.AUTO_CANCELLED);
            default -> List.of(OrderStatusCode.PLACED);
        };
    }

    private static String actorTypeFor(OrderStatusCode step) {
        return switch (step) {
            case PLACED -> "CUSTOMER";
            case RESTAURANT_ACCEPTED, PREPARING, READY_FOR_PICKUP, SELF_PICKUP_COMPLETED, REJECTED -> "STORE_OWNER";
            case CANCELLED -> "CUSTOMER";
            case AUTO_CANCELLED -> "SYSTEM";
            default -> "DELIVERY";
        };
    }

    /** Same purpose as {@link DemoCatalogSeeder#backfillJourney} - these orders are inserted directly, bypassing every service method that would normally log each transition. */
    private void backfillJourney(Order order, OrderStatusCode finalStatus, Long customerUserId, Long riderUserId) {
        if (!orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).isEmpty()) {
            return;
        }
        List<OrderStatusCode> path = pathFor(finalStatus);
        OrderStatusCode previous = null;
        for (int i = 0; i < path.size(); i++) {
            OrderStatusCode step = path.get(i);
            String actorType = actorTypeFor(step);
            Long actorUserId = "DELIVERY".equals(actorType) && riderUserId != null ? riderUserId : customerUserId;

            OrderStatusLog logEntry = new OrderStatusLog();
            logEntry.setOrderId(order.getId());
            logEntry.setFromStatus(previous != null ? previous.name() : null);
            logEntry.setToStatus(step.name());
            logEntry.setActorType(actorType);
            logEntry.setActorUserId(actorUserId);
            logEntry.setNote(step == OrderStatusCode.PLACED ? "Order placed" : step == finalStatus ? order.getOrderComment() : null);
            logEntry.setCreatedAt(order.getCreatedAt().plusMinutes(i * 8L));
            orderStatusLogRepository.save(logEntry);
            previous = step;
        }
    }
}

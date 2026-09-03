package com.pureeats.app.seeder;

import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.AcceptDelivery;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.OrderItem;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.TripDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.domain.enums.PaymentMode;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderItemRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.order.repository.TripDetailRepository;
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
 * Seeds ~45 orders spread across the last 45 days - purely so the admin Reports page (top items,
 * revenue trend, orders by status, top restaurants, top delivery partners) has enough volume and
 * date spread to look like a real report instead of a single day's spike. {@link DemoCatalogSeeder}
 * and {@link DemoOrderJourneySeeder} between them only produce same-day/narrow-window data and
 * (for the journey seeder) no {@link TripDetail} rows at all - {@code topRiders} would otherwise
 * come back empty. Runs after both ({@code @Order(4)}).
 * <p>
 * Idempotent by {@code uniqueOrderId}, same pattern as the other seeders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(4)
public class DemoReportDataSeeder implements ApplicationRunner {

    private static final int ORDER_COUNT = 45;
    private static final int SPAN_DAYS = 45;

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final TripDetailRepository tripDetailRepository;
    private final OrderStatusLogRepository orderStatusLogRepository;
    private final OrderStatusService orderStatusService;

    private record Line(Item item, int quantity) {
    }

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
            log.warn("Demo customers/riders/restaurants not found - skipping report-volume seeding "
                    + "(DemoUserSeeder/DemoCatalogSeeder may not have run yet)");
            return;
        }
        List<User> customers = List.of(customer1.get(), customer2.get());
        List<User> riders = List.of(rider1.get(), rider2.get());
        PaymentMode[] paymentModes = {PaymentMode.COD, PaymentMode.WALLET, PaymentMode.RAZORPAY};

        int created = 0;
        for (int i = 0; i < ORDER_COUNT; i++) {
            if (seedOrder(i, restaurants, customers, riders, paymentModes)) {
                created++;
            }
        }
        log.info("Demo report-data seed complete: {} new orders across {} days", created, SPAN_DAYS);
    }

    private boolean seedOrder(int i, List<Restaurant> restaurants, List<User> customers, List<User> riders, PaymentMode[] paymentModes) {
        String uniqueOrderId = "PE-REPORT-" + String.format("%03d", i + 1);
        if (orderRepository.findAll().stream().anyMatch(o -> uniqueOrderId.equals(o.getUniqueOrderId()))) {
            return false;
        }

        Restaurant restaurant = restaurants.get(i % restaurants.size());
        List<Item> menu = itemRepository.findByRestaurantId(restaurant.getId().intValue());
        if (menu.isEmpty()) {
            return false;
        }
        User customer = customers.get(i % customers.size());
        User rider = riders.get(i % riders.size());

        int lineCount = 1 + (i % 2);
        List<Line> lines = new java.util.ArrayList<>();
        for (int l = 0; l < lineCount; l++) {
            Item item = menu.get((i + l) % menu.size());
            lines.add(new Line(item, 1 + ((i + l) % 3)));
        }
        BigDecimal itemTotal = lines.stream()
                .map(line -> line.item().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = itemTotal.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal restaurantCharge = restaurant.getRestaurantCharges() != null
                ? itemTotal.multiply(restaurant.getRestaurantCharges()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(10);
        BigDecimal deliveryCharge = restaurant.getDeliveryCharges() != null ? restaurant.getDeliveryCharges() : BigDecimal.valueOf(25);
        BigDecimal driverTip = i % 6 == 0 ? BigDecimal.valueOf(20) : BigDecimal.ZERO;
        BigDecimal payable = itemTotal.add(tax).add(restaurantCharge).add(deliveryCharge).add(driverTip);

        // Weighted outcome: mostly delivered, with a realistic sprinkle of every other terminal
        // state plus a few still in-progress, so "orders by status" isn't a single solid bar.
        OrderStatusCode finalStatus = switch (i % 12) {
            case 10 -> OrderStatusCode.CANCELLED;
            case 11 -> i % 24 == 11 ? OrderStatusCode.REJECTED : OrderStatusCode.RETURNED;
            case 9 -> OrderStatusCode.AUTO_CANCELLED;
            case 8 -> OrderStatusCode.PREPARING;
            default -> OrderStatusCode.DELIVERED;
        };
        boolean delivered = finalStatus == OrderStatusCode.DELIVERED;
        boolean hasRider = delivered || finalStatus == OrderStatusCode.RETURNED;

        // Spread roughly evenly across the last SPAN_DAYS days, oldest first, with some hour/minute jitter.
        long dayOffset = (long) i * SPAN_DAYS / ORDER_COUNT;
        LocalDateTime createdAt = LocalDateTime.now().minusDays(SPAN_DAYS - dayOffset)
                .withHour(9 + (i % 11)).withMinute((i * 13) % 60).withSecond(0).withNano(0);

        Order order = new Order();
        order.setUniqueOrderId(uniqueOrderId);
        order.setOrderstatusId(orderStatusService.idFor(finalStatus));
        order.setUserId(customer.getId().intValue());
        order.setRestaurantId(restaurant.getId().intValue());
        order.setAddress("221B, 12th Cross, Bengaluru");
        order.setLocation("{\"latitude\":\"12.9352\",\"longitude\":\"77.6146\"}");
        order.setTax(tax);
        order.setRestaurantCharge(restaurantCharge);
        order.setDeliveryCharge(deliveryCharge);
        order.setDriverTipAmount(driverTip);
        order.setTotal(itemTotal);
        order.setPayable(payable);
        order.setPaymentMode(paymentModes[i % paymentModes.length].name());
        order.setDeliveryPin(String.valueOf(5000 + i));
        order.setDeliveryType(0);
        order.setPrepareTime(15 + (i % 4) * 5);
        order.setOrderFrom("SEED");
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt.plusMinutes(45));
        order = orderRepository.save(order);

        for (Line line : lines) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId().intValue());
            orderItem.setItemId(line.item().getId().intValue());
            orderItem.setName(line.item().getName());
            orderItem.setQuantity(line.quantity());
            orderItem.setPrice(line.item().getPrice());
            orderItem.setCreatedAt(order.getCreatedAt());
            orderItem.setUpdatedAt(order.getCreatedAt());
            orderItemRepository.save(orderItem);
        }

        if (hasRider) {
            AcceptDelivery accept = new AcceptDelivery();
            accept.setOrderId(order.getId().intValue());
            accept.setUserId(rider.getId().intValue());
            accept.setCustomerId(customer.getId().intValue());
            accept.setIsComplete(delivered);
            acceptDeliveryRepository.save(accept);

            if (delivered) {
                BigDecimal riderEarning = payable.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal restaurantEarning = itemTotal.subtract(restaurantCharge);
                TripDetail trip = new TripDetail();
                trip.setOrderId(order.getId().intValue());
                trip.setCustomerId(order.getUserId());
                trip.setRestaurantId(order.getRestaurantId());
                trip.setRiderId(rider.getId().intValue());
                trip.setDeliveryCollectionId(0);
                trip.setDistanceTravelled(BigDecimal.valueOf(1.5 + (i % 8)));
                trip.setRiderEarning(riderEarning);
                trip.setRestaurantEarning(restaurantEarning);
                trip.setCashCollectedFromCustomer("COD".equals(order.getPaymentMode()) ? payable : BigDecimal.ZERO);
                trip.setCashOnHold(BigDecimal.ZERO);
                trip.setIsSettlementDone(0);
                trip.setCreatedAt(order.getUpdatedAt());
                trip.setUpdatedAt(order.getUpdatedAt());
                tripDetailRepository.save(trip);
            }
        }

        backfillJourney(order, finalStatus, customer.getId().longValue(), hasRider ? rider.getId().longValue() : null);
        return true;
    }

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
            case PLACED, CANCELLED -> "CUSTOMER";
            case RESTAURANT_ACCEPTED, PREPARING, READY_FOR_PICKUP, SELF_PICKUP_COMPLETED, REJECTED -> "STORE_OWNER";
            case AUTO_CANCELLED -> "SYSTEM";
            default -> "DELIVERY";
        };
    }

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
            logEntry.setNote(step == OrderStatusCode.PLACED ? "Order placed" : null);
            logEntry.setCreatedAt(order.getCreatedAt().plusMinutes(i * 6L));
            orderStatusLogRepository.save(logEntry);
            previous = step;
        }
    }
}

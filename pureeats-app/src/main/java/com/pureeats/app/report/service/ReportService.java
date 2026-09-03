package com.pureeats.app.report.service;

import com.pureeats.app.report.dto.OrderStatusSliceRow;
import com.pureeats.app.report.dto.RevenueTrendPoint;
import com.pureeats.app.report.dto.TopItemReportRow;
import com.pureeats.app.report.dto.TopRestaurantReportRow;
import com.pureeats.app.report.dto.TopRiderReportRow;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.OrderItem;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.TripDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.order.repository.OrderItemRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sales/item/delivery-partner performance reports for the admin Reports page - every method takes
 * the same {@code from}/{@code to} (nullable, inclusive, yyyy-MM-dd) + optional {@code restaurantId}
 * shape the React admin's date-range picker already sends. Aggregation happens in Java over a
 * date-scoped fetch rather than a single grouped SQL query, matching {@code DashboardService}'s
 * existing style - the demo/admin data volumes here don't warrant more than that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TripDetailRepository tripDetailRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final OrderStatusService orderStatusService;

    public List<TopItemReportRow> topItems(String from, String to, Long restaurantId, int limit) {
        List<Order> ordersInRange = fetchOrders(from, to, restaurantId);
        if (ordersInRange.isEmpty()) {
            return List.of();
        }
        Map<Integer, Order> orderById = ordersInRange.stream()
                .collect(Collectors.toMap(o -> o.getId().intValue(), o -> o, (a, b) -> a));
        List<OrderItem> lines = orderItemRepository.findByOrderIdIn(List.copyOf(orderById.keySet()));

        record Agg(String name, Integer restaurantId, int quantity, BigDecimal revenue) {
            Agg plus(OrderItem oi) {
                return new Agg(name, restaurantId, quantity + oi.getQuantity(),
                        revenue.add(oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity()))));
            }
        }
        Map<Integer, Agg> totals = new LinkedHashMap<>();
        for (OrderItem oi : lines) {
            Order order = orderById.get(oi.getOrderId());
            if (order == null) {
                continue;
            }
            totals.merge(oi.getItemId(), new Agg(oi.getName(), order.getRestaurantId(), oi.getQuantity(),
                            oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity()))),
                    (existing, fresh) -> existing.plus(oi));
        }

        Map<Long, String> restaurantNames = restaurantNamesFor(
                totals.values().stream().map(Agg::restaurantId).filter(java.util.Objects::nonNull).map(Integer::longValue).toList());

        return totals.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Agg>>comparingInt(e -> e.getValue().quantity()).reversed())
                .limit(limit)
                .map(e -> new TopItemReportRow(e.getKey().longValue(), e.getValue().name(),
                        restaurantNames.getOrDefault(e.getValue().restaurantId() != null ? e.getValue().restaurantId().longValue() : -1L, "Unknown"),
                        e.getValue().quantity(), e.getValue().revenue()))
                .toList();
    }

    public List<RevenueTrendPoint> revenueTrend(String from, String to, Long restaurantId) {
        LocalDate toDate = parseDate(to, LocalDate.now());
        LocalDate earliestFallback = orderRepository.findAll().stream()
                .map(Order::getCreatedAt).filter(java.util.Objects::nonNull).map(LocalDateTime::toLocalDate)
                .min(Comparator.naturalOrder()).orElse(toDate);
        LocalDate fromDate = from != null && !from.isBlank() ? LocalDate.parse(from) : earliestFallback;
        // Cap at 90 daily buckets even for a very wide range, same as the mock fixture's chart cap.
        long spanDays = Math.min(90, Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1));
        LocalDate bucketStart = toDate.minusDays(spanDays - 1);

        Map<String, RevenueTrendPoint> buckets = new LinkedHashMap<>();
        for (LocalDate d = bucketStart; !d.isAfter(toDate); d = d.plusDays(1)) {
            buckets.put(d.toString(), new RevenueTrendPoint(d.toString(), BigDecimal.ZERO, 0));
        }

        List<Order> ordersInRange = fetchOrders(from, to, restaurantId);
        for (Order order : ordersInRange) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            String key = order.getCreatedAt().toLocalDate().toString();
            RevenueTrendPoint existing = buckets.get(key);
            if (existing != null) {
                buckets.put(key, new RevenueTrendPoint(key, existing.revenue().add(order.getTotal()), existing.orders() + 1));
            }
        }
        return List.copyOf(buckets.values());
    }

    public List<OrderStatusSliceRow> ordersByStatus(String from, String to, Long restaurantId) {
        List<Order> ordersInRange = fetchOrders(from, to, restaurantId);
        Map<Integer, Long> countByStatusId = ordersInRange.stream()
                .collect(Collectors.groupingBy(Order::getOrderstatusId, Collectors.counting()));
        return orderStatusService.listAll().stream()
                .map(status -> new OrderStatusSliceRow(status.name(), countByStatusId.getOrDefault(status.id().intValue(), 0L)))
                .toList();
    }

    public List<TopRestaurantReportRow> topRestaurants(String from, String to, int limit) {
        List<Order> ordersInRange = fetchOrders(from, to, null);
        record Agg(BigDecimal revenue, int orders) {
        }
        Map<Integer, Agg> totals = new LinkedHashMap<>();
        for (Order order : ordersInRange) {
            if (order.getRestaurantId() == null) {
                continue;
            }
            totals.merge(order.getRestaurantId(), new Agg(order.getTotal(), 1),
                    (existing, fresh) -> new Agg(existing.revenue().add(order.getTotal()), existing.orders() + 1));
        }
        Map<Long, String> names = restaurantNamesFor(totals.keySet().stream().map(Integer::longValue).toList());
        return totals.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Agg>, BigDecimal>comparing(e -> e.getValue().revenue()).reversed())
                .limit(limit)
                .map(e -> new TopRestaurantReportRow(e.getKey().longValue(), names.getOrDefault(e.getKey().longValue(), "Unknown"),
                        e.getValue().revenue(), e.getValue().orders()))
                .toList();
    }

    public List<TopRiderReportRow> topRiders(String from, String to, int limit) {
        LocalDateTime fromDt = from != null && !from.isBlank() ? LocalDate.parse(from).atStartOfDay() : null;
        LocalDateTime toDt = parseDate(to, LocalDate.now()).atTime(LocalTime.MAX);
        List<TripDetail> trips = fromDt == null
                ? tripDetailRepository.findByCreatedAtLessThanEqual(toDt)
                : tripDetailRepository.findByCreatedAtBetween(fromDt, toDt);

        record Agg(BigDecimal earnings, int deliveries) {
        }
        Map<Integer, Agg> totals = new LinkedHashMap<>();
        for (TripDetail trip : trips) {
            totals.merge(trip.getRiderId(), new Agg(trip.getRiderEarning(), 1),
                    (existing, fresh) -> new Agg(existing.earnings().add(trip.getRiderEarning()), existing.deliveries() + 1));
        }
        Map<Long, String> riderNames = userRepository.findAllById(totals.keySet().stream().map(Integer::longValue).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return totals.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Agg>, BigDecimal>comparing(e -> e.getValue().earnings()).reversed())
                .limit(limit)
                .map(e -> new TopRiderReportRow(e.getKey().longValue(),
                        riderNames.getOrDefault(e.getKey().longValue(), "Rider #" + e.getKey()),
                        e.getValue().deliveries(), e.getValue().earnings()))
                .toList();
    }

    private List<Order> fetchOrders(String from, String to, Long restaurantId) {
        LocalDateTime fromDt = from != null && !from.isBlank() ? LocalDate.parse(from).atStartOfDay() : null;
        LocalDateTime toDt = parseDate(to, LocalDate.now()).atTime(LocalTime.MAX);
        Integer restaurantIdInt = restaurantId != null ? restaurantId.intValue() : null;

        if (restaurantIdInt == null) {
            return fromDt == null
                    ? orderRepository.findByCreatedAtLessThanEqual(toDt)
                    : orderRepository.findByCreatedAtBetween(fromDt, toDt);
        }
        return fromDt == null
                ? orderRepository.findByRestaurantIdAndCreatedAtLessThanEqual(restaurantIdInt, toDt)
                : orderRepository.findByRestaurantIdAndCreatedAtBetween(restaurantIdInt, fromDt, toDt);
    }

    private Map<Long, String> restaurantNamesFor(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return restaurantRepository.findAllById(ids).stream().collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("Could not parse report date '{}', defaulting to {}", value, fallback);
            return fallback;
        }
    }
}

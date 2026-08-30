package com.pureeats.app.dashboard.service;

import com.pureeats.app.dashboard.dto.AdminDashboardResponse;
import com.pureeats.app.dashboard.dto.OrderStatusCountResponse;
import com.pureeats.app.dashboard.dto.OwnerDashboardResponse;
import com.pureeats.app.dashboard.dto.TopRestaurantResponse;
import com.pureeats.app.dashboard.dto.TrendPointResponse;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.AcceptDelivery;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Rating;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.domain.enums.Role;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.rating.repository.RatingRepository;
import com.pureeats.rating.service.RatingService;
import com.pureeats.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates dashboard stats across catalog, order, user and rating data. Lives in pureeats-app
 * (like {@link com.pureeats.app.seeder.DemoCatalogSeeder}) since it's the one module that already
 * depends on every other module - a dashboard service anywhere else would need a dependency it
 * doesn't otherwise have.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter TREND_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final Set<OrderStatusCode> TERMINAL_STATUSES =
            Set.of(OrderStatusCode.DELIVERED, OrderStatusCode.CANCELLED, OrderStatusCode.SELF_PICKUP_COMPLETED);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final RatingRepository ratingRepository;
    private final OrderStatusService orderStatusService;
    private final AdminUserService adminUserService;
    private final RatingService ratingService;

    public AdminDashboardResponse adminDashboard() {
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.sumTotal();
        long activeRestaurants = restaurantRepository.countByIsActiveTrueAndIsAcceptedTrue();
        long totalRestaurants = restaurantRepository.count();
        long totalCustomers = adminUserService.listUsers(Role.CUSTOMER, null, PageRequest.of(0, 1)).totalElements();
        long totalRiders = adminUserService.listUsers(Role.DELIVERY, null, PageRequest.of(0, 1)).totalElements();
        long onlineRiders = acceptDeliveryRepository.findByIsCompleteFalse().stream()
                .map(AcceptDelivery::getUserId).distinct().count();
        double avgRating = ratingRepository.findAll().stream().mapToInt(Rating::getRating).average().orElse(0);

        List<OrderStatusCountResponse> ordersByStatus = orderRepository.countGroupedByStatus().stream()
                .map(row -> new OrderStatusCountResponse(statusName(row.getStatusId()), row.getCnt()))
                .toList();

        List<TopRestaurantResponse> topRestaurants = mapTopRestaurants(orderRepository.revenueByRestaurant(PageRequest.of(0, 5)));

        List<TrendPointResponse> trend = buildTrend(orderRepository.findByCreatedAtGreaterThanEqual(fourteenDaysAgo()));

        return new AdminDashboardResponse(totalOrders, totalRevenue, activeRestaurants, totalRestaurants,
                totalCustomers, onlineRiders, totalRiders, avgRating, ordersByStatus, trend, topRestaurants);
    }

    public OwnerDashboardResponse ownerDashboard(Long restaurantId) {
        Integer restaurantIdInt = restaurantId.intValue();
        long totalOrders = orderRepository.countByRestaurantId(restaurantIdInt);
        BigDecimal totalRevenue = orderRepository.sumTotalForRestaurant(restaurantIdInt);
        long pendingOrders = orderRepository.findByRestaurantId(restaurantIdInt).stream()
                .filter(o -> !TERMINAL_STATUSES.contains(orderStatusService.codeFor(o.getOrderstatusId())))
                .count();
        double avgRating = ratingService.restaurantAverage(restaurantId).average();
        List<TrendPointResponse> trend = buildTrend(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantIdInt, fourteenDaysAgo()));

        return new OwnerDashboardResponse(totalOrders, totalRevenue, pendingOrders, avgRating, trend);
    }

    private String statusName(Integer statusId) {
        OrderStatusCode code = orderStatusService.codeFor(statusId);
        if (code == null) {
            return "Unknown";
        }
        String[] words = code.name().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return label.toString();
    }

    private List<TopRestaurantResponse> mapTopRestaurants(List<OrderRepository.RestaurantRevenueProjection> rows) {
        Map<Long, String> names = restaurantRepository.findAllById(rows.stream().map(r -> r.getRestaurantId().longValue()).toList())
                .stream().collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
        return rows.stream()
                .map(row -> new TopRestaurantResponse(
                        names.getOrDefault(row.getRestaurantId().longValue(), "Unknown"),
                        row.getRevenue() != null ? row.getRevenue() : BigDecimal.ZERO,
                        row.getCnt()))
                .toList();
    }

    private List<TrendPointResponse> buildTrend(List<Order> ordersInWindow) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, TrendPointResponse> buckets = new java.util.LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            String label = now.minusDays(i).format(TREND_LABEL_FORMAT);
            buckets.put(label, new TrendPointResponse(label, 0, BigDecimal.ZERO));
        }
        for (Order order : ordersInWindow) {
            if (order.getCreatedAt() == null) continue;
            String label = order.getCreatedAt().format(TREND_LABEL_FORMAT);
            TrendPointResponse existing = buckets.get(label);
            if (existing != null) {
                buckets.put(label, new TrendPointResponse(label, existing.orders() + 1, existing.revenue().add(order.getTotal())));
            }
        }
        return List.copyOf(buckets.values());
    }

    private LocalDateTime fourteenDaysAgo() {
        return LocalDateTime.now().minusDays(13).with(LocalTime.MIN);
    }
}

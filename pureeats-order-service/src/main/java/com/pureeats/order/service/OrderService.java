package com.pureeats.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.dto.CouponRedemptionResult;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.repository.RestaurantUserRepository;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.enums.DeliveryType;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.service.NotificationDispatchService;
import com.pureeats.order.dto.*;
import com.pureeats.order.repository.OrderItemAddonRepository;
import com.pureeats.order.repository.OrderItemRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.user.repository.AddressRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemAddonRepository orderItemAddonRepository;
    private final OrderStatusService orderStatusService;
    private final OrderPricingService orderPricingService;
    private final WalletService walletService;

    private final RestaurantRepository restaurantRepository;
    private final ItemRepository itemRepository;
    private final AddonRepository addonRepository;
    private final RestaurantUserRepository restaurantUserRepository;
    private final CouponService couponService;
    private final AddressRepository addressRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final UserRepository userRepository;
    private final OrderStatusLogService orderStatusLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + request.restaurantId()));
        if (!Boolean.TRUE.equals(restaurant.getIsActive()) || !Boolean.TRUE.equals(restaurant.getIsAccepted())) {
            throw new BadRequestException("This restaurant is not currently accepting orders");
        }

        Address address = addressRepository.findById(request.addressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + request.addressId()));
        if (!address.getUserId().equals(userId.intValue())) {
            throw new ForbiddenException("This address does not belong to you");
        }

        Order order = new Order();
        order.setUniqueOrderId(generateUniqueOrderId());
        order.setUserId(userId.intValue());
        order.setRestaurantId(restaurant.getId().intValue());
        order.setAddress(address.getHouse() + ", " + address.getAddress());
        order.setLocation("{\"latitude\":\"" + address.getLatitude() + "\",\"longitude\":\"" + address.getLongitude() + "\"}");
        order.setPaymentMode(request.paymentMode().name());
        order.setDeliveryType(request.deliveryType() == DeliveryType.SELF_PICKUP ? 1 : 0);
        order.setOrderComment(request.orderComment());
        order.setOrderFrom("API");
        order.setDeliveryPin(generatePin());
        order.setDriverTipAmount(request.driverTipAmount() != null ? request.driverTipAmount() : BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        BigDecimal itemTotal = BigDecimal.ZERO;
        record Line(Item item, int quantity, List<Addon> addons) {
        }
        List<Line> lines = request.items().stream().map(itemRequest -> {
            Item item = itemRepository.findById(itemRequest.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemRequest.itemId()));
            if (!item.getRestaurantId().equals(restaurant.getId().intValue()) || !Boolean.TRUE.equals(item.getIsActive())) {
                throw new BadRequestException("Item " + item.getName() + " is not available at this restaurant");
            }
            List<Addon> addons = itemRequest.selectedAddonIds() == null ? List.of()
                    : itemRequest.selectedAddonIds().stream()
                    .map(id -> addonRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Addon not found: " + id)))
                    .toList();
            return new Line(item, itemRequest.quantity(), addons);
        }).toList();

        for (Line line : lines) {
            BigDecimal addonTotal = line.addons().stream().map(Addon::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            itemTotal = itemTotal.add(line.item().getPrice().add(addonTotal).multiply(BigDecimal.valueOf(line.quantity())));
        }

        BigDecimal discount = BigDecimal.ZERO;
        boolean freeDelivery = false;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            boolean isFirstOrder = orderRepository.findByUserIdOrderByCreatedAtDesc(userId.intValue()).isEmpty();
            CouponRedemptionResult redemption = couponService.recordUsage(request.couponCode(),
                    restaurant.getId().intValue(), itemTotal, userId.intValue(), isFirstOrder);
            discount = redemption.discountAmount();
            freeDelivery = redemption.freeDelivery();
            order.setCouponCode(redemption.code());
            order.setCouponName(redemption.name());
        }

        BigDecimal amountAfterDiscount = itemTotal.subtract(discount);
        BigDecimal tax = orderPricingService.tax(amountAfterDiscount);
        BigDecimal restaurantCharge = orderPricingService.restaurantCharge(restaurant, amountAfterDiscount);
        boolean isSelfPickup = request.deliveryType() == DeliveryType.SELF_PICKUP;
        DeliveryChargeResult deliveryChargeResult = orderPricingService.computeDeliveryCharge(
                restaurant, isSelfPickup, freeDelivery, address.getLatitude(), address.getLongitude());
        BigDecimal deliveryCharge = deliveryChargeResult.amount();
        BigDecimal payable = amountAfterDiscount.add(tax).add(restaurantCharge).add(deliveryCharge).add(order.getDriverTipAmount());

        order.setTotal(itemTotal);
        order.setDiscountAmount(discount);
        order.setTax(tax);
        order.setRestaurantCharge(restaurantCharge);
        order.setDeliveryCharge(deliveryCharge);
        order.setPayable(payable);
        order.setPricingBreakdown(serializeBreakdown(new PricingBreakdown(
                itemTotal, discount, amountAfterDiscount, tax, orderPricingService.taxPercentage(),
                restaurantCharge, restaurant.getRestaurantCharges(), deliveryCharge, deliveryChargeResult.basis(),
                deliveryChargeResult.distanceKm(), restaurant.getLatitude(), restaurant.getLongitude(),
                address.getLatitude(), address.getLongitude())));

        boolean autoAccept = Boolean.TRUE.equals(restaurant.getAutoAcceptable());
        order.setOrderstatusId(orderStatusService.idFor(autoAccept ? OrderStatusCode.RESTAURANT_ACCEPTED : OrderStatusCode.PLACED));

        order = orderRepository.save(order);
        orderStatusLogService.record(order.getId(), null,
                autoAccept ? OrderStatusCode.RESTAURANT_ACCEPTED : OrderStatusCode.PLACED, "CUSTOMER", userId, "Order placed");

        for (Line line : lines) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId().intValue());
            orderItem.setItemId(line.item().getId().intValue());
            orderItem.setName(line.item().getName());
            orderItem.setQuantity(line.quantity());
            orderItem.setPrice(line.item().getPrice());
            orderItem.setCreatedAt(LocalDateTime.now());
            orderItem.setUpdatedAt(LocalDateTime.now());
            orderItem = orderItemRepository.save(orderItem);

            for (Addon addon : line.addons()) {
                OrderItemAddon orderItemAddon = new OrderItemAddon();
                orderItemAddon.setOrderitemId(orderItem.getId().intValue());
                orderItemAddon.setAddonName(addon.getName());
                orderItemAddon.setAddonPrice(addon.getPrice());
                orderItemAddon.setAddonCategoryName("");
                orderItemAddon.setCreatedAt(LocalDateTime.now());
                orderItemAddon.setUpdatedAt(LocalDateTime.now());
                orderItemAddonRepository.save(orderItemAddon);
            }
        }

        if (order.getPaymentMode().equals("WALLET")) {
            walletService.debit(userId, payable, "Order #" + order.getUniqueOrderId());
        }

        notifyOwners(restaurant.getId(), order.getUniqueOrderId());

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> myOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId.intValue()).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = findOrThrow(orderId);
        if (!order.getUserId().equals(userId.intValue())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        return toResponse(order);
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = findOrThrow(orderId);
        if (!order.getUserId().equals(userId.intValue())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        OrderStatusCode current = orderStatusService.codeFor(order.getOrderstatusId());
        if (current == OrderStatusCode.DELIVERED || current == OrderStatusCode.CANCELLED) {
            throw new BadRequestException("This order can no longer be cancelled");
        }

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.CANCELLED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        if ("WALLET".equals(order.getPaymentMode())) {
            walletService.credit(userId, order.getPayable(), "Refund for cancelled order #" + order.getUniqueOrderId());
        }
        orderStatusLogService.record(order.getId(), current, OrderStatusCode.CANCELLED, "CUSTOMER", userId, "Cancelled by customer");
    }

    /** Admin override - the only path that can jump straight to any status legal from the current one, not just the next role-specific step. */
    @Transactional
    public OrderResponse adminUpdateStatus(Long adminUserId, Long orderId, OrderStatusCode toStatus) {
        Order order = findOrThrow(orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        if (!OrderStatusTransitions.isLegal(from, toStatus)) {
            throw new BadRequestException("Cannot change order status from "
                    + (from != null ? from.name() : "UNKNOWN") + " to " + toStatus.name());
        }

        order.setOrderstatusId(orderStatusService.idFor(toStatus));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        if (toStatus == OrderStatusCode.CANCELLED && "WALLET".equals(order.getPaymentMode())) {
            walletService.credit(order.getUserId().longValue(), order.getPayable(),
                    "Refund for cancelled order #" + order.getUniqueOrderId());
        }

        orderStatusLogService.record(order.getId(), from, toStatus, "ADMIN", adminUserId, "Updated by admin");
        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Order status updated",
                "Your order #" + order.getUniqueOrderId() + " is now " + toStatus.name());
        return toResponse(order);
    }

    Order findOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /** Admin listing - every order, optionally filtered by restaurant/status/uniqueOrderId search. */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> listPaged(Long restaurantId, Integer statusId, String search, Pageable pageable) {
        Page<Order> page = orderRepository.findPage(
                restaurantId != null ? restaurantId.intValue() : null, statusId, search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toAdminSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Admin detail - unlike {@link #getOrder}, not scoped to the caller owning the order. */
    @Transactional(readOnly = true)
    public OrderResponse getOrderForAdmin(Long orderId) {
        return toResponse(findOrThrow(orderId));
    }

    private AdminOrderSummaryResponse toAdminSummary(Order order) {
        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        String customerName = userRepository.findById(order.getUserId().longValue()).map(User::getName).orElse("Unknown");
        String restaurantName = restaurantRepository.findById(order.getRestaurantId().longValue()).map(Restaurant::getName).orElse("Unknown");
        int itemCount = orderItemRepository.findByOrderId(order.getId().intValue()).size();
        return new AdminOrderSummaryResponse(order.getId(), order.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN",
                order.getUserId().longValue(), order.getRestaurantId().longValue(), customerName, restaurantName, itemCount,
                order.getTotal(), order.getPayable(), order.getPaymentMode(), order.getCouponCode(), order.getCreatedAt());
    }

    OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId().intValue());
        List<OrderItemResponse> itemResponses = items.stream().map(oi -> {
            List<OrderItemAddonResponse> addons = orderItemAddonRepository.findByOrderitemId(oi.getId().intValue())
                    .stream().map(a -> new OrderItemAddonResponse(a.getAddonCategoryName(), a.getAddonName(), a.getAddonPrice()))
                    .toList();
            return new OrderItemResponse(oi.getId(), oi.getItemId().longValue(), oi.getName(), oi.getQuantity(), oi.getPrice(), addons);
        }).toList();

        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        User customer = userRepository.findById(order.getUserId().longValue()).orElse(null);
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        List<String> legalNextStatuses = OrderStatusTransitions.legalNext(status).stream().map(Enum::name).toList();

        OrderCustomerSummary customerSummary = new OrderCustomerSummary(order.getUserId().longValue(),
                customer != null ? customer.getName() : "Unknown", customer != null ? customer.getEmail() : null,
                customer != null ? customer.getPhone() : null);
        OrderRestaurantSummary restaurantSummary = new OrderRestaurantSummary(order.getRestaurantId().longValue(),
                restaurant != null ? restaurant.getName() : "Unknown", restaurant != null ? restaurant.getContactNumber() : null);
        var liveCoupon = order.getCouponCode() == null ? null : couponService.findByCode(order.getCouponCode()).orElse(null);
        OrderCouponSummary couponSummary = order.getCouponCode() == null ? null : new OrderCouponSummary(
                liveCoupon != null ? liveCoupon.id() : null, order.getCouponCode(), order.getCouponName(),
                liveCoupon != null ? liveCoupon.discountType() : null, order.getDiscountAmount());

        return new OrderResponse(order.getId(), order.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN",
                order.getOrderstatusId(), customerSummary, restaurantSummary, couponSummary, itemResponses,
                order.getAddress(), order.getTax(), order.getRestaurantCharge(),
                order.getDeliveryCharge(), order.getDriverTipAmount(), order.getDiscountAmount(), order.getTotal(), order.getPayable(),
                order.getPaymentMode(), order.getDeliveryPin(), order.getOrderComment(),
                order.getTransactionId(), order.getDeliveryType(), order.getOrderFrom(), order.getCreatedAt(),
                legalNextStatuses, deserializeBreakdown(order.getPricingBreakdown()));
    }

    private String serializeBreakdown(PricingBreakdown breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception e) {
            log.warn("Failed to serialize pricing breakdown for order", e);
            return null;
        }
    }

    private PricingBreakdown deserializeBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PricingBreakdown.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize pricing breakdown for order", e);
            return null;
        }
    }

    private OrderSummaryResponse toSummary(Order order) {
        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        return new OrderSummaryResponse(order.getId(), order.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN",
                order.getRestaurantId().longValue(), order.getPayable(), order.getCreatedAt());
    }

    private void notifyOwners(Long restaurantId, String uniqueOrderId) {
        restaurantUserRepository.findByRestaurantId(restaurantId).stream()
                .map(RestaurantUser::getUserId)
                .distinct()
                .forEach(ownerId -> notificationDispatchService.notifyUser(ownerId, "New order received",
                        "Order #" + uniqueOrderId + " has been placed"));
    }

    private static String generateUniqueOrderId() {
        return "PE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String generatePin() {
        return String.valueOf(1000 + RANDOM.nextInt(9000));
    }
}

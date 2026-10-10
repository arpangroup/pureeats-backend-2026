package com.pureeats.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.order.dto.*;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.DeliveryGuyLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryOrderService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final GpsTableRepository gpsTableRepository;
    private final TripDetailRepository tripDetailRepository;
    private final DeliveryCollectionRepository deliveryCollectionRepository;
    private final DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    private final RestaurantPayoutService restaurantPayoutService;
    private final WalletService walletService;
    private final OrderNotificationService orderNotificationService;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final OrderStatusLogService orderStatusLogService;
    private final DeliveryGuyLocationService deliveryGuyLocationService;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPricingService orderPricingService;
    private final ObjectMapper objectMapper;
    private final OrderStatusLogRepository orderStatusLogRepository;
    private final com.pureeats.user.service.RiderStatusLogService riderStatusLogService;
    private final com.pureeats.media.service.MediaAssetService mediaAssetService;
    private final OrderTimingService orderTimingService;
    private final RiderDispatchService riderDispatchService;
    private final com.pureeats.user.repository.LoginHistoryRepository loginHistoryRepository;
    private final com.pureeats.media.storage.MediaUrlResolver mediaUrlResolver;

    /** Statuses after which an assignment is no longer "active" even if its AcceptDelivery row was never marked complete (e.g. the customer cancelled after a rider was assigned). */
    private static final java.util.Set<OrderStatusCode> TERMINAL_STATUSES = java.util.EnumSet.of(
            OrderStatusCode.DELIVERED, OrderStatusCode.SELF_PICKUP_COMPLETED, OrderStatusCode.CANCELLED,
            OrderStatusCode.REJECTED, OrderStatusCode.RETURNED, OrderStatusCode.AUTO_CANCELLED);

    /** Fallback only (tests / settings unavailable) - the live basis comes from Settings, see {@link #basis()}. */
    @Value("${pureeats.commission.basis:DELIVERY_CHARGE_ONLY}")
    private CommissionBasis commissionBasis;

    /** Settings -> Delivery Application -> Earnings -> Delivery partner earns from. */
    private CommissionBasis basis() {
        CommissionBasis fromSettings = orderPricingService.riderCommissionBasis();
        return fromSettings != null ? fromSettings : commissionBasis;
    }

    /** An order still sitting unassigned in RESTAURANT_ACCEPTED/READY_FOR_PICKUP past this age is
     * treated as stale (abandoned, or demo/seed data) rather than genuinely available - see
     * {@link #availableOrders}. Ops can reassign a specific stale order by hand (admin override)
     * regardless of this window; this only bounds what's surfaced to riders browsing/polling. */
    @Value("${pureeats.delivery.available-order-window-hours:2}")
    private long availableOrderWindowHours;

    /**
     * Rider-scoped (not just role-scoped): {@code payoutEstimate} is computed against the CALLING
     * rider's own commission rate, mirroring {@link #creditRiderAndSettle}'s math exactly, so what's
     * shown here is what they'd actually be credited if they accept - two riders with different
     * commission rates would see different payout numbers for the same order.
     */
    @Transactional
    public List<DeliveryAvailableOrderResponse> availableOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        if (!rider.isApproved()) {
            return List.of(); // not approved yet - no orders offered
        }
        List<Integer> statusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(availableOrderWindowHours);
        // Only orders from stores this partner may take (Settings -> New order alerts: linked stores / nearby / all).
        java.util.function.Predicate<Restaurant> mayTake = riderDispatchService.ruleFor(rider);
        Map<Integer, Restaurant> restaurants = new java.util.HashMap<>();
        return orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(statusIds, cutoff).stream()
                .filter(o -> o.getDeliveryType() == 0 && acceptDeliveryRepository.findByOrderId(o.getId().intValue()).isEmpty())
                .filter(o -> mayTake.test(restaurants.computeIfAbsent(o.getRestaurantId(),
                        id -> restaurantRepository.findById(id.longValue()).orElse(null))))
                .map(o -> toAvailableOrderResponse(o, rider))
                .toList();
    }

    private DeliveryAvailableOrderResponse toAvailableOrderResponse(Order order, DeliveryGuyDetail rider) {
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        String customerLat = null;
        String customerLng = null;
        try {
            if (order.getLocation() != null) {
                JsonNode node = objectMapper.readTree(order.getLocation());
                customerLat = node.path("latitude").asText(null);
                customerLng = node.path("longitude").asText(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse stored location JSON for order {}: {}", order.getId(), e.getMessage());
        }

        BigDecimal distanceKm = restaurant != null
                ? orderPricingService.distanceKm(restaurant, customerLat, customerLng)
                : BigDecimal.ZERO;

        BigDecimal commissionBase = basis() == CommissionBasis.DELIVERY_CHARGE_ONLY
                ? java.util.Objects.requireNonNullElse(order.getDeliveryCharge(), BigDecimal.ZERO) : order.getTotal();
        BigDecimal payoutEstimate = commissionBase.multiply(orderPricingService.riderCommissionRate(rider))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        int itemsCount = orderItemRepository.findByOrderId(order.getId().intValue()).size();

        BigDecimal pickupDistanceKm = restaurant != null && rider.getLastLat() != null && rider.getLastLng() != null
                ? orderPricingService.distanceKm(approx(rider.getLastLat()), approx(rider.getLastLng()),
                        restaurant.getLatitude(), restaurant.getLongitude())
                : null;

        return new DeliveryAvailableOrderResponse(
                order.getId(), order.getUniqueOrderId(),
                restaurant != null ? restaurant.getName() : "Unknown",
                restaurant != null ? restaurant.getAddress() : "",
                restaurant != null ? parseCoordinate(restaurant.getLatitude()) : BigDecimal.ZERO,
                restaurant != null ? parseCoordinate(restaurant.getLongitude()) : BigDecimal.ZERO,
                order.getAddress(),
                parseCoordinate(customerLat), parseCoordinate(customerLng),
                distanceKm, payoutEstimate, itemsCount, order.getCreatedAt(),
                tipOf(order), pickupDistanceKm, distanceKm,
                java.util.Optional.ofNullable(orderStatusService.codeFor(order.getOrderstatusId())).map(Enum::name).orElse("UNKNOWN"),
                order.getPaymentMode(), pickupDueAt(order));
    }

    /**
     * The partner's position rounded to ~100 m for distance lookups: they move between polls, and with Google
     * Distance Matrix on, an exact position would never hit the cache - one paid lookup per poll per order.
     */
    private static String approx(BigDecimal coordinate) {
        return coordinate.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal tipOf(Order order) {
        return order.getDriverTipAmount() != null && order.getDriverTipAmount().signum() > 0 ? order.getDriverTipAmount() : BigDecimal.ZERO;
    }

    private BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public OrderResponse acceptToDeliver(Long riderUserId, Long orderId) {
        log.info("Rider {} accepting order {} for delivery", riderUserId, orderId);
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        requireApproved(rider);
        Order order = orderService.findOrThrow(orderId);
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        if (!riderDispatchService.canReceive(rider, restaurant)) {
            log.warn("Rejected delivery acceptance for order {} by rider {}: not offered to them ({} mode)", orderId, riderUserId, riderDispatchService.mode());
            throw new ForbiddenException("ORDER_NOT_OFFERED", "This order is for delivery partners linked to the restaurant or nearby - it wasn't offered to you.");
        }
        if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            log.warn("Rejected delivery acceptance for order {}: already assigned to a rider", orderId);
            throw new BadRequestException("This order has already been assigned to a rider");
        }
        long activeCount = countDeliveriesInProgress(riderUserId);
        int limit = rider.getMaxAcceptDeliveryLimit() != null && rider.getMaxAcceptDeliveryLimit() > 0 ? rider.getMaxAcceptDeliveryLimit() : 1;
        if (activeCount >= limit) {
            log.warn("Rejected delivery acceptance for rider {}: at concurrent delivery limit ({}/{})",
                    riderUserId, activeCount, limit);
            throw new BadRequestException("You have reached your maximum concurrent delivery limit");
        }

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(order.getId().intValue());
        accept.setUserId(riderUserId.intValue());
        accept.setCustomerId(order.getUserId());
        accept.setIsComplete(false);
        acceptDeliveryRepository.save(accept);

        recordAssignment(order, "DELIVERY", riderUserId, null);
        log.info("Rider {} self-accepted order {}", riderUserId, orderId);

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(),
                riderAssignedData(order.getId(), rider, riderUserId));
        return orderService.toResponse(order);
    }

    /** Admin override - assigns a specific rider to a specific order directly, skipping the rider's own concurrent-delivery-limit check (an explicit admin decision, not a rider self-service action). */
    @Transactional
    public OrderResponse assignDriverAsAdmin(Long adminUserId, Long orderId, Long riderUserId) {
        log.info("Admin {} assigning rider {} to order {}", adminUserId, riderUserId, orderId);
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        try {
            requireApproved(rider);
        } catch (ForbiddenException e) {
            // Same rule, worded for the admin.
            log.warn("Rejected admin assignment of rider {} to order {}: {}", riderUserId, orderId, e.getMessage());
            throw new BadRequestException(rider.isApproved() ? e.getMessage()
                    : "This delivery partner isn't approved yet - approve them under Partner Approvals before assigning orders.");
        }
        Order order = orderService.findOrThrow(orderId);
        if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            log.warn("Rejected admin driver assignment for order {}: already assigned to a rider", orderId);
            throw new BadRequestException("This order has already been assigned to a rider");
        }

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(order.getId().intValue());
        accept.setUserId(riderUserId.intValue());
        accept.setCustomerId(order.getUserId());
        accept.setIsComplete(false);
        acceptDeliveryRepository.save(accept);

        recordAssignment(order, "ADMIN", adminUserId, "Driver assigned by admin");
        log.info("Admin {} assigned rider {} to order {}", adminUserId, riderUserId, orderId);

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(),
                riderAssignedData(order.getId(), rider, riderUserId));
        orderNotificationService.notify(NotificationRecipientRole.DELIVERY_PARTNER, riderUserId, "New delivery assigned",
                "You've been assigned to deliver order #" + order.getUniqueOrderId());
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse pickedUp(Long riderUserId, Long orderId) {
        log.info("Rider {} marking order {} as picked up", riderUserId, orderId);
        Order order = ownedByRider(riderUserId, orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        // A partner can't skip ahead: the store (or an admin) must have marked the food ready first.
        if (!FOOD_READY.contains(from)) {
            log.warn("Rejected pickup of order {} by rider {}: status is {}, not ready yet", orderId, riderUserId, from);
            throw new BadRequestException("The restaurant hasn't marked this order ready yet - you can mark it picked up once it's ready.");
        }
        if (mediaAssetService.countForOwner(PICKUP_PHOTO_OWNER, order.getId()) == 0) {
            throw new BadRequestException("Take at least one photo of the packed order before marking it picked up.");
        }
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.PICKED_UP));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.PICKED_UP, "DELIVERY", riderUserId, null);
        log.info("Order {} transitioned {} -> PICKED_UP by rider {}", orderId, from, riderUserId);
        return orderService.toResponse(order);
    }

    /** Statuses in which the food is ready to be handed over (a rider assigned once it's ready moves it to RIDER_ASSIGNED). */
    private static final java.util.Set<OrderStatusCode> FOOD_READY = java.util.EnumSet.of(
            OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.RIDER_ASSIGNED);
    /** Statuses after pickup in which the order can be completed. */
    private static final java.util.Set<OrderStatusCode> OUT_FOR_DELIVERY = java.util.EnumSet.of(
            OrderStatusCode.PICKED_UP, OrderStatusCode.ON_THE_WAY, OrderStatusCode.ARRIVED);

    public static final String PICKUP_PHOTO_OWNER = "ORDER_PICKUP";
    /** Photos at handover to the customer - same media_assets table, different owner type. */
    public static final String DELIVERY_PHOTO_OWNER = "ORDER_DELIVERY";
    public static final int MAX_PICKUP_PHOTOS = 3;
    private static final String ASSIGNED_WHILE_PREPARING = "Delivery partner assigned" + OrderStatusLogService.ASSIGNED_WHILE_PREPARING_SUFFIX;

    /**
     * Records a rider assignment. If the food is already READY_FOR_PICKUP the order moves to RIDER_ASSIGNED as
     * before; otherwise it keeps its kitchen status (Accepted / Preparing) so the restaurant can still mark it
     * ready and the partner sees the real preparation state - the AcceptDelivery row is what assigns the rider.
     */
    private void recordAssignment(Order order, String actorType, Long actorUserId, String note) {
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setUpdatedAt(LocalDateTime.now());
        if (from == OrderStatusCode.READY_FOR_PICKUP) {
            order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.RIDER_ASSIGNED));
            orderRepository.save(order);
            orderStatusLogService.record(order.getId(), from, OrderStatusCode.RIDER_ASSIGNED, actorType, actorUserId, note);
        } else {
            orderRepository.save(order);
            orderStatusLogService.record(order.getId(), from, from, actorType, actorUserId,
                    note != null ? note + OrderStatusLogService.ASSIGNED_WHILE_PREPARING_SUFFIX : ASSIGNED_WHILE_PREPARING);
        }
    }

    /** The partner has reached the customer's location - the customer is told they're at the door. */
    @Transactional
    public OrderResponse arrived(Long riderUserId, Long orderId) {
        Order order = ownedByRider(riderUserId, orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        if (from != OrderStatusCode.PICKED_UP && from != OrderStatusCode.ON_THE_WAY) {
            throw new BadRequestException("You can only mark arrival after picking up the order.");
        }
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.ARRIVED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.ARRIVED, "DELIVERY", riderUserId, "Reached the customer's location");
        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Your delivery partner has arrived",
                "Your order #" + order.getUniqueOrderId() + " is at your location - please keep your delivery PIN ready.",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.ARRIVED.name()));
        log.info("Order {} transitioned {} -> ARRIVED by rider {}", orderId, from, riderUserId);
        return orderService.toResponse(order);
    }

    /** Uploads one photo of the packed order (max {@value #MAX_PICKUP_PHOTOS}), before pickup. Stored as media assets owned by the order - no extra table. */
    @Transactional
    public com.pureeats.media.dto.MediaUploadResponse uploadPickupPhoto(Long riderUserId, Long orderId, org.springframework.web.multipart.MultipartFile file) {
        Order order = ownedByRider(riderUserId, orderId);
        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        if (OUT_FOR_DELIVERY.contains(status) || TERMINAL_STATUSES.contains(status)) {
            throw new BadRequestException("Pickup photos can only be added before the order is picked up.");
        }
        if (mediaAssetService.countForOwner(PICKUP_PHOTO_OWNER, order.getId()) >= MAX_PICKUP_PHOTOS) {
            throw new BadRequestException("You can add up to " + MAX_PICKUP_PHOTOS + " photos - remove one to retake it.");
        }
        return mediaAssetService.upload(file, PICKUP_PHOTO_OWNER, order.getId(), riderUserId);
    }

    @Transactional
    public void deletePickupPhoto(Long riderUserId, Long orderId, Long mediaId) {
        Order order = ownedByRider(riderUserId, orderId);
        if (OUT_FOR_DELIVERY.contains(orderStatusService.codeFor(order.getOrderstatusId()))) {
            throw new BadRequestException("Photos can't be removed after pickup.");
        }
        mediaAssetService.delete(PICKUP_PHOTO_OWNER, order.getId(), mediaId);
    }

    /** One photo at handover (max {@value #MAX_PICKUP_PHOTOS}) - only after the partner marked Arrived, before delivery. */
    @Transactional
    public com.pureeats.media.dto.MediaUploadResponse uploadDeliveryPhoto(Long riderUserId, Long orderId, org.springframework.web.multipart.MultipartFile file) {
        Order order = ownedByRider(riderUserId, orderId);
        if (orderStatusService.codeFor(order.getOrderstatusId()) != OrderStatusCode.ARRIVED) {
            throw new BadRequestException("Mark that you've reached the customer before taking the delivery photo.");
        }
        if (mediaAssetService.countForOwner(DELIVERY_PHOTO_OWNER, order.getId()) >= MAX_PICKUP_PHOTOS) {
            throw new BadRequestException("You can add up to " + MAX_PICKUP_PHOTOS + " photos - remove one to retake it.");
        }
        return mediaAssetService.upload(file, DELIVERY_PHOTO_OWNER, order.getId(), riderUserId);
    }

    @Transactional
    public void deleteDeliveryPhoto(Long riderUserId, Long orderId, Long mediaId) {
        Order order = ownedByRider(riderUserId, orderId);
        if (orderStatusService.codeFor(order.getOrderstatusId()) != OrderStatusCode.ARRIVED) {
            throw new BadRequestException("Photos can't be removed after delivery.");
        }
        mediaAssetService.delete(DELIVERY_PHOTO_OWNER, order.getId(), mediaId);
    }

    @Transactional(readOnly = true)
    public List<PickupPhotoResponse> deliveryPhotos(Long orderId) {
        return photos(DELIVERY_PHOTO_OWNER, orderId);
    }

    @Transactional(readOnly = true)
    public List<PickupPhotoResponse> deliveryPhotosForRider(Long riderUserId, Long orderId) {
        ownedByRider(riderUserId, orderId);
        return deliveryPhotos(orderId);
    }

    private List<PickupPhotoResponse> photos(String owner, Long orderId) {
        return mediaAssetService.listForOwner(owner, orderId).stream()
                .map(a -> new PickupPhotoResponse(a.getId(), mediaUrlResolver.resolve(a.getStorageKey()), a.getCreatedAt()))
                .toList();
    }

    /** Pickup photos for an order (rider: own orders only; admin: any). */
    @Transactional(readOnly = true)
    public List<PickupPhotoResponse> pickupPhotos(Long orderId) {
        return photos(PICKUP_PHOTO_OWNER, orderId);
    }

    @Transactional(readOnly = true)
    public List<PickupPhotoResponse> pickupPhotosForRider(Long riderUserId, Long orderId) {
        ownedByRider(riderUserId, orderId);
        return pickupPhotos(orderId);
    }

    /**
     * Live drop-off ETA for the partner: travel time from their last reported position to the customer (Google
     * Maps when configured, else estimated). Before pickup it's from the restaurant.
     */
    @Transactional(readOnly = true)
    public com.pureeats.order.dto.DeliveryEtaResponse liveEta(Long riderUserId, Long orderId) {
        Order order = ownedByRider(riderUserId, orderId);
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        String[] customer = customerLatLng(order);
        boolean fromRider = rider.getLastLat() != null && rider.getLastLng() != null && OUT_FOR_DELIVERY.contains(orderStatusService.codeFor(order.getOrderstatusId()));
        int minutes;
        BigDecimal km;
        if (fromRider) {
            minutes = orderTimingService.travelMinutes(approx(rider.getLastLat()), approx(rider.getLastLng()), customer[0], customer[1]);
            km = orderPricingService.distanceKm(approx(rider.getLastLat()), approx(rider.getLastLng()), customer[0], customer[1]);
        } else {
            Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
            minutes = restaurant != null ? orderTimingService.travelMinutes(restaurant.getLatitude(), restaurant.getLongitude(), customer[0], customer[1]) : 0;
            km = restaurant != null ? orderPricingService.distanceKm(restaurant, customer[0], customer[1]) : BigDecimal.ZERO;
        }
        return new com.pureeats.order.dto.DeliveryEtaResponse(minutes, km, fromRider ? "RIDER" : "RESTAURANT", LocalDateTime.now());
    }

    private String[] customerLatLng(Order order) {
        try {
            JsonNode node = objectMapper.readTree(order.getLocation());
            return new String[]{node.path("latitude").asText(null), node.path("longitude").asText(null)};
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    /** Online/offline changes and sign-ins for the rider's Activity screen. */
    @Transactional(readOnly = true)
    public RiderActivityResponse activity(Long riderUserId) {
        List<RiderActivityResponse.StatusChange> status = riderStatusLogService.recent(riderUserId, 50).stream()
                .map(l -> new RiderActivityResponse.StatusChange(Boolean.TRUE.equals(l.getIsOnline()), l.getReason(),
                        statusMessage(Boolean.TRUE.equals(l.getIsOnline()), l.getReason()), l.getCreatedAt()))
                .toList();
        List<RiderActivityResponse.Login> logins = loginHistoryRepository
                .findByUserId(riderUserId, org.springframework.data.domain.PageRequest.of(0, 30,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "occurredAt")))
                .stream()
                .map(h -> new RiderActivityResponse.Login(h.getOccurredAt(), h.getLoginMethod() != null ? h.getLoginMethod().name() : null,
                        h.getStatus(), h.getUserAgent(), java.util.stream.Stream.of(h.getCity(), h.getRegion(), h.getCountry())
                                .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "))))
                .toList();
        return new RiderActivityResponse(status, logins);
    }

    private static String statusMessage(boolean online, String reason) {
        if (online) return "You went online";
        if (DeliveryGuyDetail.OFFLINE_REASON_INACTIVITY.equals(reason)) return "Set offline automatically - your app stopped sharing your location";
        if (DeliveryGuyDetail.OFFLINE_REASON_ADMIN.equals(reason)) return "Set offline by the PureEats team";
        return "You went offline";
    }

    /** When the food should be ready for collection: restaurant acceptance + the order's prep time (default 20 min). */
    private LocalDateTime pickupDueAt(Order order) {
        return orderTimingService.prepDueAt(order);
    }

    /** A cash amount above the bill by more than this is almost certainly a typo (e.g. 4800 for 480) - rejected. */
    static final BigDecimal MAX_COD_CHANGE = new BigDecimal("2000");

    /** @param cashCollected cash on delivery: what the partner received - at least the amount due; the extra goes to the customer's wallet. */
    @Transactional
    public OrderResponse deliver(Long riderUserId, Long orderId, String deliveryPin, BigDecimal cashCollected) {
        log.info("Rider {} attempting to complete delivery for order {}", riderUserId, orderId);
        Order order = ownedByRider(riderUserId, orderId);
        if (orderStatusService.codeFor(order.getOrderstatusId()) != OrderStatusCode.ARRIVED) {
            throw new BadRequestException("Mark that you've reached the customer's location first.");
        }
        if (mediaAssetService.countForOwner(DELIVERY_PHOTO_OWNER, order.getId()) == 0) {
            throw new BadRequestException("Take a photo of the order being handed over before confirming delivery.");
        }
        BigDecimal cash = null;
        if (isCashOnDelivery(order)) {
            BigDecimal due = order.getPayable();
            if (cashCollected == null) {
                throw new BadRequestException("Confirm how much cash you collected from the customer.");
            }
            cash = cashCollected.setScale(2, RoundingMode.HALF_UP);
            if (cash.compareTo(due) < 0) {
                throw new BadRequestException("Collect the full amount due (" + rupees(due) + ") - you entered " + rupees(cash) + ".");
            }
            if (cash.subtract(due).compareTo(MAX_COD_CHANGE) > 0) {
                throw new BadRequestException("That's " + rupees(cash.subtract(due)) + " more than the bill - check the amount collected.");
            }
        }
        return completeDelivery(order, deliveryPin, "DELIVERY", riderUserId,
                cash != null && cash.compareTo(order.getPayable()) > 0
                        ? "Verified by delivery PIN - cash collected " + rupees(cash) + " for " + rupees(order.getPayable())
                        : "Verified by delivery PIN", cash);
    }

    private static boolean isCashOnDelivery(Order order) {
        return "COD".equals(order.getPaymentMode());
    }

    private static String rupees(BigDecimal amount) {
        return "\u20b9" + amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** The customer confirms delivery themself by reading out the same PIN - e.g. handed to the rider in person. */
    @Transactional
    public OrderResponse customerConfirmDelivery(Long customerUserId, Long orderId, String deliveryPin) {
        log.info("Customer {} attempting to self-confirm delivery for order {}", customerUserId, orderId);
        Order order = orderService.findOrThrow(orderId);
        if (!order.getUserId().equals(customerUserId.intValue())) {
            log.warn("User {} attempted to confirm delivery of order {} which does not belong to them", customerUserId, orderId);
            throw new ForbiddenException("This order does not belong to you");
        }
        return completeDelivery(order, deliveryPin, "CUSTOMER", customerUserId, "Confirmed by customer via delivery PIN", null);
    }

    /**
     * Shared by both delivery-confirmation paths - whoever confirms it, the assigned rider (if
     * any) is who actually gets credited/logged in the trip, not necessarily the caller.
     */
    /**
     * Admin marks an order Delivered (status override). Runs the same completion as the PIN flow - so the
     * partner's earning and the restaurant's payout are recorded (trip_details) and credited - instead of
     * only flipping the status, which used to leave the order's earnings unrecorded ("Projected").
     */
    @Transactional
    public OrderResponse adminMarkDelivered(Long adminUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        if (!OrderStatusTransitions.isLegal(from, OrderStatusCode.DELIVERED)) {
            throw new BadRequestException("Cannot change order status from " + (from != null ? from.name() : "UNKNOWN") + " to DELIVERED");
        }
        return finishDelivery(order, "ADMIN", adminUserId, "Marked delivered by admin", null);
    }

    private OrderResponse completeDelivery(Order order, String deliveryPin, String actorType, Long actorUserId, String note, BigDecimal cashCollected) {
        OrderStatusCode current = orderStatusService.codeFor(order.getOrderstatusId());
        if (!OUT_FOR_DELIVERY.contains(current)) {
            log.warn("Rejected delivery completion for order {} by {} {}: status is {}", order.getId(), actorType, actorUserId, current);
            throw new BadRequestException("This order hasn't been picked up yet, so it can't be marked delivered.");
        }
        if (!order.getDeliveryPin().equalsIgnoreCase(deliveryPin)) {
            log.warn("Rejected delivery completion for order {} by {} {}: incorrect delivery PIN", order.getId(), actorType, actorUserId);
            throw new BadRequestException("Incorrect delivery PIN");
        }
        return finishDelivery(order, actorType, actorUserId, note, cashCollected);
    }

    /** @param cashCollected cash the partner confirmed receiving (COD), or null = exactly the amount due. */
    private OrderResponse finishDelivery(Order order, String actorType, Long actorUserId, String note, BigDecimal cashCollected) {

        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.DELIVERED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.DELIVERED, actorType, actorUserId, note);
        log.info("Order {} transitioned {} -> DELIVERED ({} {})", order.getId(), from, actorType, actorUserId);

        AcceptDelivery assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue()).orElse(null);
        if (assignment != null) {
            assignment.setIsComplete(true);
            acceptDeliveryRepository.save(assignment);
            creditRiderAndSettle(order, assignment.getUserId().longValue(), LocalDateTime.now(), cashCollected);
        } else {
            log.debug("Order {} delivered with no rider assignment on record (likely self-pickup)", order.getId());
        }

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Order delivered",
                "Your order #" + order.getUniqueOrderId() + " has been delivered. Enjoy your meal!",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.DELIVERED.name()));
        creditCashChange(order, cashCollected);
        return orderService.toResponse(order);
    }

    /**
     * Cash on delivery where the customer handed over more than the bill (e.g. ₹500 for ₹480): the extra goes to
     * their wallet, with a note saying why, instead of the partner having to find change.
     */
    private void creditCashChange(Order order, BigDecimal cashCollected) {
        if (cashCollected == null || !isCashOnDelivery(order)) return;
        BigDecimal change = cashCollected.subtract(order.getPayable());
        if (change.signum() <= 0) return;
        walletService.credit(order.getUserId().longValue(), change,
                "Change from cash payment for order #" + order.getUniqueOrderId() + ": paid " + rupees(cashCollected)
                        + " for a " + rupees(order.getPayable()) + " bill");
        log.info("Credited {} change to customer {} for COD order {} ({} paid for {})",
                change, order.getUserId(), order.getId(), cashCollected, order.getPayable());
        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Change added to your wallet",
                rupees(change) + " from your cash payment for order #" + order.getUniqueOrderId() + " is in your PureEats wallet.");
    }

    /**
     * Records the earnings of a DELIVERED order that has none - orders an admin marked delivered before that
     * path recorded them (it used to only change the status). Same credit as a normal delivery; no-op when
     * the order already has a trip record, so it's safe to run more than once.
     * @return true when earnings were recorded now
     */
    @Transactional
    public boolean recordMissingEarnings(Long orderId, LocalDateTime deliveredAt) {
        Order order = orderService.findOrThrow(orderId);
        if (orderStatusService.codeFor(order.getOrderstatusId()) != OrderStatusCode.DELIVERED) return false;
        if (tripDetailRepository.findByOrderId(order.getId().intValue()).isPresent()) return false;
        AcceptDelivery assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue()).orElse(null);
        if (assignment == null) return false;
        assignment.setIsComplete(true);
        acceptDeliveryRepository.save(assignment);
        creditRiderAndSettle(order, assignment.getUserId().longValue(), deliveredAt != null ? deliveredAt : LocalDateTime.now());
        log.info("Recorded missing earnings for admin-delivered order {} (rider {})", orderId, assignment.getUserId());
        return true;
    }

    private void creditRiderAndSettle(Order order, Long riderUserId, LocalDateTime deliveredAt) {
        creditRiderAndSettle(order, riderUserId, deliveredAt, null);
    }

    /** @param cashCollected COD cash the partner confirmed receiving; null = the amount due. */
    private void creditRiderAndSettle(Order order, Long riderUserId, LocalDateTime deliveredAt, BigDecimal cashCollectedArg) {
        if (tripDetailRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            log.warn("Order {} already has its earnings recorded - not crediting again", order.getId());
            return;
        }
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        BigDecimal commissionBase = basis() == CommissionBasis.DELIVERY_CHARGE_ONLY
                ? java.util.Objects.requireNonNullElse(order.getDeliveryCharge(), BigDecimal.ZERO) : order.getTotal();
        BigDecimal riderRate = orderPricingService.riderCommissionRate(rider);
        BigDecimal riderEarning = commissionBase.multiply(riderRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        walletService.credit(riderUserId, riderEarning, "Delivery earning for order #" + order.getUniqueOrderId());
        log.debug("Credited rider {} earning {} for order {}", riderUserId, riderEarning, order.getId());
        // The customer's tip goes to the rider in full - it's part of what the customer paid but was
        // previously never credited to anyone. Its own wallet entry so the rider can see it.
        BigDecimal tip = tipOf(order);
        if (tip.signum() > 0) {
            walletService.credit(riderUserId, tip, "Tip for order #" + order.getUniqueOrderId());
            log.debug("Credited rider {} tip {} for order {}", riderUserId, tip, order.getId());
        }

        // item total − commission + packaging charge (see OrderService#restaurantPayoutFor).
        BigDecimal restaurantEarning = orderService.restaurantPayoutFor(order);
        restaurantPayoutService.recordEarning(order.getRestaurantId(), restaurantEarning);

        BigDecimal cashCollected = BigDecimal.ZERO;
        if (isCashOnDelivery(order)) {
            // The partner holds ALL the cash they took (e.g. ₹500 on a ₹480 bill) until settlement; the extra was
            // credited to the customer's wallet, so the platform is square once the partner hands it in.
            cashCollected = cashCollectedArg != null ? cashCollectedArg : order.getPayable();
            recordCashCollection(riderUserId, cashCollected, order.getUniqueOrderId());
            log.debug("Recorded COD cash collection of {} for rider {} on order {}", cashCollected, riderUserId, order.getId());
        }

        TripDetail trip = new TripDetail();
        trip.setOrderId(order.getId().intValue());
        trip.setCustomerId(order.getUserId());
        trip.setRestaurantId(order.getRestaurantId());
        trip.setRiderId(riderUserId.intValue());
        trip.setDeliveryCollectionId(0);
        trip.setDistanceTravelled(tripDistanceKm(order));
        // Earning = commission + tip, so pending settlement and analytics include what the rider was tipped.
        trip.setRiderEarning(riderEarning.add(tip));
        trip.setRestaurantEarning(restaurantEarning);
        trip.setCashCollectedFromCustomer(cashCollected);
        // COD cash the rider now holds on the platform's behalf until their next settlement.
        trip.setCashOnHold(cashCollected);
        // Snapshot of how the earning was computed, so the rider's earning breakdown stays correct
        // even if their commission rate (or the platform-wide basis) changes later.
        trip.setMeta("{\"commissionRate\":" + riderRate.toPlainString()
                + ",\"commissionBasis\":\"" + basis().name() + "\""
                + ",\"commissionBase\":" + commissionBase.toPlainString()
                + ",\"tip\":" + tip.toPlainString() + "}");
        trip.setIsSettlementDone(0);
        // Dated by when the order was delivered, so earnings/analytics land on the right day.
        trip.setCreatedAt(deliveredAt);
        trip.setUpdatedAt(LocalDateTime.now());
        tripDetailRepository.save(trip);
    }

    /** Restaurant-to-customer distance for the trip record (same straight-line estimate shown when the rider accepted). */
    private BigDecimal tripDistanceKm(Order order) {
        try {
            Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
            if (restaurant == null || order.getLocation() == null) return BigDecimal.ZERO;
            JsonNode node = objectMapper.readTree(order.getLocation());
            BigDecimal km = orderPricingService.distanceKm(restaurant, node.path("latitude").asText(null), node.path("longitude").asText(null));
            return km != null ? km : BigDecimal.ZERO;
        } catch (Exception e) {
            log.debug("Could not compute trip distance for order {}: {}", order.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public void recordGpsPing(GpsPingRequest request) {
        log.debug("Recording GPS ping for order {}", request.orderId());
        GpsTable gps = new GpsTable();
        gps.setOrderId(request.orderId().intValue());
        gps.setDeliveryLat(request.deliveryLat());
        gps.setDeliveryLong(request.deliveryLong());
        gps.setHeading(request.heading());
        gps.setBearing(request.bearing());
        gps.setCreatedAt(LocalDateTime.now());
        gps.setUpdatedAt(LocalDateTime.now());
        gpsTableRepository.save(gps);
    }

    @Transactional(readOnly = true)
    public GpsLocationResponse getGpsLocation(Long orderId) {
        GpsTable gps = gpsTableRepository.findFirstByOrderIdOrderByUpdatedAtDesc(orderId.intValue())
                .orElseThrow(() -> {
                    log.warn("No GPS ping recorded for order {}", orderId);
                    return new ResourceNotFoundException("No GPS ping recorded for this order yet");
                });
        return new GpsLocationResponse(gps.getDeliveryLat(), gps.getDeliveryLong(), gps.getHeading(), gps.getBearing());
    }

    /**
     * Self-service online/offline toggle for the signed-in rider. Going offline deliberately leaves
     * {@code lastLat}/{@code lastLng}/{@code lastSeenAt} untouched (stale-but-present) rather than
     * clearing them - an admin/ops view showing "last seen at 5:42pm near X" for an offline rider is
     * more useful than showing nothing, and it costs nothing since {@link #availableOrders} /
     * anything rider-nearby-facing should already be filtering on {@code isOnline} itself.
     */
    @Transactional
    public void setOnlineStatus(Long riderUserId, boolean isOnline) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        if (isOnline) requireApproved(rider);
        LocalDateTime now = LocalDateTime.now();
        boolean changed = !Boolean.valueOf(isOnline).equals(rider.getIsOnline());
        if (changed) {
            rider.setStatusChangedAt(now);
        }
        rider.setIsOnline(isOnline);
        rider.setOfflineReason(isOnline ? null : DeliveryGuyDetail.OFFLINE_REASON_SELF);
        // Going online counts as "seen" - otherwise a rider whose first GPS fix is still pending could be
        // swept straight back offline by RiderInactivityScheduler on the strength of an old lastSeenAt.
        if (isOnline) rider.setLastSeenAt(now);
        deliveryGuyDetailRepository.save(rider);
        if (changed) riderStatusLogService.record(riderUserId, isOnline, isOnline ? null : DeliveryGuyDetail.OFFLINE_REASON_SELF);
        log.info("Rider {} is now {}", riderUserId, isOnline ? "ONLINE" : "OFFLINE");
    }

    /** The signed-in rider's own server-side status - lets the app notice it was force-stopped (see RiderInactivityScheduler) while backgrounded. */
    @Transactional(readOnly = true)
    public RiderStatusResponse getOnlineStatus(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return new RiderStatusResponse(Boolean.TRUE.equals(rider.getIsOnline()), rider.getOfflineReason(),
                rider.getStatusChangedAt(), rider.getLastSeenAt());
    }

    /**
     * Self-service, principal-scoped location ping - resolves the caller's own {@link
     * DeliveryGuyDetail} the same way every other rider action does ({@link #riderProfile}), then
     * delegates the actual write to {@link DeliveryGuyLocationService#updateLocation}, the same
     * method the admin id-scoped endpoint calls, so the write logic itself isn't duplicated.
     */
    @Transactional
    public void updateMyLocation(Long riderUserId, String lat, String lng) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        BigDecimal latitude = new BigDecimal(lat);
        BigDecimal longitude = new BigDecimal(lng);
        deliveryGuyLocationService.updateLocation(rider.getId(), latitude, longitude);
        recordBreadcrumbs(riderUserId, latitude, longitude);
    }

    /** Statuses during which the rider is physically on the order - their pings become the order's tracking path. */
    private static final java.util.Set<OrderStatusCode> ON_THE_ROAD = java.util.EnumSet.of(
            OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.PREPARING, OrderStatusCode.READY_FOR_PICKUP,
            OrderStatusCode.RIDER_ASSIGNED, OrderStatusCode.PICKED_UP, OrderStatusCode.ON_THE_WAY, OrderStatusCode.ARRIVED);

    /** Below this movement (~15 m) a new ping just refreshes the last point's timestamp instead of adding a point. */
    private static final double MIN_BREADCRUMB_DEGREES = 0.00015;
    private static final int MAX_PATH_POINTS = 400;
    private static final long STALE_AFTER_SECONDS = 120;

    /**
     * Appends the rider's position to the GPS trail (GpsTable) of every order they're currently out on,
     * so the customer's tracking map can show where the rider is and the path they've taken. The rider
     * app only ever sends rider-level pings (POST /delivery/location) - the order-scoped POST /delivery/gps
     * was never called, which is why GpsTable stayed empty and the customer map had nothing to show.
     */
    private void recordBreadcrumbs(Long riderUserId, BigDecimal lat, BigDecimal lng) {
        LocalDateTime now = LocalDateTime.now();
        for (AcceptDelivery accept : acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue())) {
            Order order = orderRepository.findById(accept.getOrderId().longValue()).orElse(null);
            if (order == null || !ON_THE_ROAD.contains(orderStatusService.codeFor(order.getOrderstatusId()))) continue;
            GpsTable last = gpsTableRepository.findFirstByOrderIdOrderByUpdatedAtDesc(accept.getOrderId()).orElse(null);
            if (last != null && closeTo(last, lat, lng)) {
                last.setUpdatedAt(now);
                gpsTableRepository.save(last);
                continue;
            }
            GpsTable point = new GpsTable();
            point.setOrderId(accept.getOrderId());
            point.setDeliveryLat(lat.toPlainString());
            point.setDeliveryLong(lng.toPlainString());
            point.setCreatedAt(now);
            point.setUpdatedAt(now);
            gpsTableRepository.save(point);
        }
    }

    private static boolean closeTo(GpsTable last, BigDecimal lat, BigDecimal lng) {
        try {
            return Math.abs(Double.parseDouble(last.getDeliveryLat()) - lat.doubleValue()) < MIN_BREADCRUMB_DEGREES
                    && Math.abs(Double.parseDouble(last.getDeliveryLong()) - lng.doubleValue()) < MIN_BREADCRUMB_DEGREES;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Live tracking for the customer's own order: restaurant + the order's delivery point, and while a
     * rider is on it, their latest fix (order trail first, falling back to the rider's last known
     * position) and the trail since assignment (downsampled to at most {@link #MAX_PATH_POINTS}).
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse trackingForCustomer(Long customerUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        if (!order.getUserId().equals(customerUserId.intValue())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        OrderTrackingResponse.Point restaurantPoint = restaurant != null
                ? point(restaurant.getLatitude(), restaurant.getLongitude()) : null;

        OrderTrackingResponse.Point destination = null;
        try {
            if (order.getLocation() != null) {
                JsonNode node = objectMapper.readTree(order.getLocation());
                destination = point(node.path("latitude").asText(null), node.path("longitude").asText(null));
            }
        } catch (Exception e) {
            log.warn("Could not parse stored location JSON for order {}: {}", order.getId(), e.getMessage());
        }

        OrderTrackingResponse.RiderPosition rider = null;
        List<OrderTrackingResponse.Point> path = List.of();
        if (status != null && ON_THE_ROAD.contains(status)) {
            List<GpsTable> trail = gpsTableRepository.findByOrderIdOrderByCreatedAtAsc(order.getId().intValue());
            path = downsample(trail).stream()
                    .map(g -> point(g.getDeliveryLat(), g.getDeliveryLong()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!trail.isEmpty()) {
                GpsTable last = trail.get(trail.size() - 1);
                OrderTrackingResponse.Point p = point(last.getDeliveryLat(), last.getDeliveryLong());
                LocalDateTime at = last.getUpdatedAt() != null ? last.getUpdatedAt() : last.getCreatedAt();
                if (p != null) rider = riderPosition(p.lat(), p.lng(), at);
            } else {
                rider = acceptDeliveryRepository.findByOrderId(order.getId().intValue())
                        .flatMap(a -> userRepository.findById(a.getUserId().longValue()))
                        .filter(u -> u.getDeliveryGuyDetailId() != null)
                        .flatMap(u -> deliveryGuyDetailRepository.findById(u.getDeliveryGuyDetailId().longValue()))
                        .filter(d -> d.getLastLat() != null && d.getLastLng() != null)
                        .map(d -> riderPosition(d.getLastLat(), d.getLastLng(), d.getLastSeenAt()))
                        .orElse(null);
            }
        }
        return new OrderTrackingResponse(order.getId(), status != null ? status.name() : "UNKNOWN", restaurantPoint, destination, rider, path);
    }

    private static OrderTrackingResponse.RiderPosition riderPosition(BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        boolean stale = at == null || at.isBefore(LocalDateTime.now().minusSeconds(STALE_AFTER_SECONDS));
        return new OrderTrackingResponse.RiderPosition(lat, lng, at, stale);
    }

    private static List<GpsTable> downsample(List<GpsTable> trail) {
        if (trail.size() <= MAX_PATH_POINTS) return trail;
        double step = (double) trail.size() / MAX_PATH_POINTS;
        List<GpsTable> out = new java.util.ArrayList<>(MAX_PATH_POINTS + 1);
        for (int i = 0; i < MAX_PATH_POINTS; i++) out.add(trail.get((int) (i * step)));
        out.add(trail.get(trail.size() - 1));
        return out;
    }

    private static OrderTrackingResponse.Point point(String lat, String lng) {
        if (lat == null || lng == null || lat.isBlank() || lng.isBlank()) return null;
        try {
            BigDecimal la = new BigDecimal(lat.trim());
            BigDecimal lo = new BigDecimal(lng.trim());
            if (la.signum() == 0 && lo.signum() == 0) return null;
            return new OrderTrackingResponse.Point(la, lo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The signed-in rider's own delivery history (every assignment, newest first), sourced from {@link AcceptDeliveryRepository} (assignments) rather than {@code OrderRepository#findByUserId} (which is the customer's own orders). */
    @Transactional(readOnly = true)
    public List<DeliveryAssignmentResponse> myOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return acceptDeliveryRepository.findByUserIdOrderByIdDesc(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(order -> toAssignmentResponse(order, rider))
                .toList();
    }

    /**
     * Every order currently assigned to the signed-in rider and not yet finished - including ones an
     * admin assigned to them directly ({@link #assignDriverAsAdmin}), which never pass through the
     * rider's own accept flow and so would otherwise never appear in the app.
     */
    @Transactional(readOnly = true)
    public List<DeliveryAssignmentResponse> activeOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(order -> !TERMINAL_STATUSES.contains(orderStatusService.codeFor(order.getOrderstatusId())))
                .sorted(java.util.Comparator.comparing(Order::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(order -> toAssignmentResponse(order, rider))
                .toList();
    }

    /**
     * Assignments that still genuinely occupy the rider. An AcceptDelivery row is only marked complete
     * on delivery, so one whose order was later cancelled/rejected/returned stays "incomplete"
     * forever - counting those raw (as this used to) permanently ate into the rider's concurrent
     * limit, and a rider with a few such leftovers could never accept another order.
     */
    public long countDeliveriesInProgress(Long riderUserId) {
        return acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(order -> !TERMINAL_STATUSES.contains(orderStatusService.codeFor(order.getOrderstatusId())))
                .count();
    }

    private DeliveryAssignmentResponse toAssignmentResponse(Order order, DeliveryGuyDetail rider) {
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        String customerLat = null;
        String customerLng = null;
        try {
            if (order.getLocation() != null) {
                JsonNode node = objectMapper.readTree(order.getLocation());
                customerLat = node.path("latitude").asText(null);
                customerLng = node.path("longitude").asText(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse stored location JSON for order {}: {}", order.getId(), e.getMessage());
        }
        BigDecimal distanceKm = restaurant != null ? orderPricingService.distanceKm(restaurant, customerLat, customerLng) : BigDecimal.ZERO;
        BigDecimal commissionBase = basis() == CommissionBasis.DELIVERY_CHARGE_ONLY ? java.util.Objects.requireNonNullElse(order.getDeliveryCharge(), BigDecimal.ZERO) : order.getTotal();
        BigDecimal payoutEstimate = commissionBase != null
                ? commissionBase.multiply(orderPricingService.riderCommissionRate(rider)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        User customer = userRepository.findById(order.getUserId().longValue()).orElse(null);
        List<DeliveryAssignmentResponse.Item> items = orderItemRepository.findByOrderId(order.getId().intValue()).stream()
                .map(i -> new DeliveryAssignmentResponse.Item(i.getName(), i.getQuantity() != null ? i.getQuantity() : 1))
                .toList();

        int riderToRestaurant = order.getRiderToRestaurantMinutes() != null ? order.getRiderToRestaurantMinutes() : orderTimingService.riderToRestaurantMinutes();
        LocalDateTime acceptedAt = null;
        LocalDateTime pickedUpAt = null;
        LocalDateTime deliveredAt = null;
        String assignedBy = "DELIVERY";
        for (var entry : orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())) {
            if (OrderStatusCode.RIDER_ASSIGNED.name().equals(entry.getToStatus())
                    || OrderStatusLogService.isAssignedWhilePreparing(entry)) {
                acceptedAt = entry.getCreatedAt();
                assignedBy = entry.getActorType() != null ? entry.getActorType() : assignedBy;
            } else if (OrderStatusCode.PICKED_UP.name().equals(entry.getToStatus())) {
                pickedUpAt = entry.getCreatedAt();
            } else if (OrderStatusCode.DELIVERED.name().equals(entry.getToStatus())) {
                deliveredAt = entry.getCreatedAt();
            }
        }

        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        return new DeliveryAssignmentResponse(
                order.getId(), order.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN", assignedBy,
                order.getRestaurantId().longValue(),
                restaurant != null ? restaurant.getName() : "Unknown",
                restaurant != null ? restaurant.getAddress() : "",
                restaurant != null ? parseCoordinate(restaurant.getLatitude()) : BigDecimal.ZERO,
                restaurant != null ? parseCoordinate(restaurant.getLongitude()) : BigDecimal.ZERO,
                restaurant != null ? restaurant.getContactNumber() : null,
                customer != null ? customer.getName() : "Customer",
                order.getAddress(),
                parseCoordinate(customerLat), parseCoordinate(customerLng),
                customer != null ? customer.getPhone() : null,
                items, order.getPayable(), order.getPaymentMode(), payoutEstimate, distanceKm, tipOf(order),
                status != null && (FOOD_READY.contains(status) || OUT_FOR_DELIVERY.contains(status)),
                pickupDueAt(order), (int) mediaAssetService.countForOwner(PICKUP_PHOTO_OWNER, order.getId()),
                (int) mediaAssetService.countForOwner(DELIVERY_PHOTO_OWNER, order.getId()),
                order.getOrderComment(),
                order.getCreatedAt(), acceptedAt, pickedUpAt, deliveredAt,
                // T2 countdown to the restaurant starts when the partner took the order.
                riderToRestaurant, acceptedAt != null ? acceptedAt.plusMinutes(riderToRestaurant) : null, order.getTravelMinutes());
    }

    private void recordCashCollection(Long riderUserId, BigDecimal amount, String uniqueOrderId) {
        DeliveryCollection collection = deliveryCollectionRepository.findByUserId(riderUserId.intValue())
                .orElseGet(() -> {
                    DeliveryCollection c = new DeliveryCollection();
                    c.setUserId(riderUserId.intValue());
                    c.setAmount(BigDecimal.ZERO);
                    c.setCreatedAt(LocalDateTime.now());
                    return c;
                });
        collection.setAmount(collection.getAmount().add(amount));
        collection.setUpdatedAt(LocalDateTime.now());
        collection = deliveryCollectionRepository.save(collection);

        DeliveryCollectionLog collectionLog = new DeliveryCollectionLog();
        collectionLog.setDeliveryCollectionId(collection.getId().intValue());
        collectionLog.setAmount(amount);
        collectionLog.setType("COD");
        collectionLog.setMessage("Cash collected for order #" + uniqueOrderId);
        collectionLog.setCreatedAt(LocalDateTime.now());
        collectionLog.setUpdatedAt(LocalDateTime.now());
        deliveryCollectionLogRepository.save(collectionLog);
    }

    private Order ownedByRider(Long riderUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        AcceptDelivery accept = acceptDeliveryRepository.findByOrderId(order.getId().intValue())
                .orElseThrow(() -> {
                    log.warn("Rejected rider action on order {}: not yet assigned to any rider", orderId);
                    return new ForbiddenException("This order has not been assigned to a rider");
                });
        if (!accept.getUserId().equals(riderUserId.intValue())) {
            log.warn("Rider {} attempted an action on order {} which is assigned to a different rider", riderUserId, orderId);
            throw new ForbiddenException("This order is not assigned to you");
        }
        return order;
    }

    /**
     * The extra {@code data} riding alongside a silent "Rider assigned" push (see
     * OrderNotificationService#notify(role, userId, title, body, data)) - lets the customer's
     * tracking page/home banner show who's coming without a follow-up fetch. {@code riderPhone}
     * comes from {@link User}, everything else from the already-loaded {@link DeliveryGuyDetail} -
     * both null-safe since a profile can be created without every optional field filled in yet.
     */
    private Map<String, Object> riderAssignedData(Long orderId, DeliveryGuyDetail rider, Long riderUserId) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("status", OrderStatusCode.RIDER_ASSIGNED.name());
        data.put("riderId", riderUserId);
        if (rider.getName() != null) data.put("riderName", rider.getName());
        if (rider.getPhoto() != null) data.put("riderPhoto", rider.getPhoto());
        if (rider.getVehicleNumber() != null) data.put("riderVehicleNumber", rider.getVehicleNumber());
        if (rider.getRating() != null) data.put("riderRating", rider.getRating());
        userRepository.findById(riderUserId).map(User::getPhone).ifPresent(phone -> data.put("riderPhone", phone));
        return data;
    }

    /** Pending or rejected applicants can't go online or take orders. */
    private void requireApproved(DeliveryGuyDetail rider) {
        // Deactivated profile, or a blocked/deleted login account: can't take orders either.
        if (Boolean.FALSE.equals(rider.getIsActive())) {
            throw new ForbiddenException("PARTNER_INACTIVE", "This delivery partner is deactivated and can't take orders.");
        }
        userRepository.findByDeliveryGuyDetailId(rider.getId().intValue()).ifPresent(u -> {
            com.pureeats.domain.enums.AccountStatus st = u.getAccountStatus() != null ? u.getAccountStatus() : com.pureeats.domain.enums.AccountStatus.ACTIVE;
            if (com.pureeats.domain.entity.User.STATUS_INACTIVE.equalsIgnoreCase(u.getIsActive()) || st != com.pureeats.domain.enums.AccountStatus.ACTIVE) {
                throw new ForbiddenException("PARTNER_BLOCKED", "This delivery partner's account is blocked or deleted and can't take orders.");
            }
        });
        if (rider.isApproved()) return;
        if (DeliveryGuyDetail.APPROVAL_REJECTED.equals(rider.getApprovalStatus())) {
            throw new ForbiddenException("PARTNER_REJECTED", "Your application was not approved" + (rider.getRejectionReason() != null ? ": " + rider.getRejectionReason() : "."));
        }
        throw new ForbiddenException("PARTNER_PENDING", "Your application is under review. You can take orders once it's approved.");
    }

    private DeliveryGuyDetail riderProfile(Long riderUserId) {
        User user = userRepository.findById(riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + riderUserId));
        if (user.getDeliveryGuyDetailId() == null) {
            log.warn("User {} attempted a rider action without a rider profile", riderUserId);
            throw new ForbiddenException("You do not have a rider profile");
        }
        return deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found"));
    }
}

package com.pureeats.order.service.cartvalidation;

import com.pureeats.catalog.dto.CouponApplyRequest;
import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Addon;
import com.pureeats.domain.entity.Address;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.DeliveryType;
import com.pureeats.order.dto.CartCouponValidationResponse;
import com.pureeats.order.dto.CartItemValidationResponse;
import com.pureeats.order.dto.CartPricingResponse;
import com.pureeats.order.dto.CartRestaurantValidationResponse;
import com.pureeats.order.dto.CartValidationRequest;
import com.pureeats.order.dto.CartValidationResponse;
import com.pureeats.order.dto.DeliveryChargeResult;
import com.pureeats.order.dto.PlaceOrderItemRequest;
import com.pureeats.order.service.OrderPricingService;
import com.pureeats.user.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stateless "is this cart still orderable, and what would it cost" check - there is no persisted
 * Cart entity (the client still owns cart state, same as {@code OrderService.placeOrder} always
 * has), this just answers the question without committing anything.
 * <p>
 * {@link #validate} is the live, non-throwing check the Cart page polls to grey out unavailable
 * items and show an up-to-date price. {@link #assertPlaceable} runs the exact same rules but
 * throws on the first issue, fail-fast - it's what {@code OrderService.placeOrder} calls so the
 * server-side guard and the live UI can never disagree about what counts as "available". The two
 * differ only in how much context they have: {@code validate()} never knows the payment mode
 * (chosen later, on Checkout) and may not have an address yet; a rule that needs either simply
 * reports nothing when it's null (see {@link CartValidationContext}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartValidationService {

    private final RestaurantRepository restaurantRepository;
    private final ItemRepository itemRepository;
    private final AddonRepository addonRepository;
    private final AddressRepository addressRepository;
    private final CouponService couponService;
    private final OrderPricingService orderPricingService;
    private final List<CartValidationRule> rules;

    @Transactional(readOnly = true)
    public CartValidationResponse validate(CartValidationRequest request, Long userId) {
        Restaurant restaurant = findRestaurant(request.restaurantId());
        List<CartLine> lines = toLines(request.items());
        boolean isSelfPickup = request.deliveryType() == DeliveryType.SELF_PICKUP;
        String[] addressLatLng = resolveAddressLatLng(request.addressId(), userId);
        BigDecimal distanceKm = isSelfPickup ? null : orderPricingService.distanceKm(restaurant, addressLatLng[0], addressLatLng[1]);
        // Zero (both coordinates missing) isn't a real "in range" answer - don't let DeliveryRadiusRule act on it.
        BigDecimal distanceForRules = (distanceKm != null && addressLatLng[0] != null) ? distanceKm : null;

        CartValidationContext context = buildContext(restaurant, lines, request.deliveryType(), distanceForRules, null, userId);
        List<CartIssue> issues = evaluateAll(context);

        CartIssue restaurantIssue = issues.stream().filter(i -> i.itemId() == null).findFirst().orElse(null);
        Map<Long, String> itemIssues = issues.stream()
                .filter(i -> i.itemId() != null)
                .collect(Collectors.toMap(CartIssue::itemId, CartIssue::reason, (a, b) -> a));

        List<CartItemValidationResponse> itemResponses = lines.stream()
                .map(line -> new CartItemValidationResponse(line.itemId(), !itemIssues.containsKey(line.itemId()), itemIssues.get(line.itemId())))
                .toList();

        List<CartLine> availableLines = lines.stream().filter(line -> !itemIssues.containsKey(line.itemId())).toList();
        BigDecimal itemTotal = availableLines.stream()
                .map(line -> lineTotal(context.itemFor(line), line))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CartCouponValidationResponse couponResponse = null;
        BigDecimal discount = BigDecimal.ZERO;
        boolean freeDelivery = false;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            try {
                CouponApplyResponse preview = couponService.preview(
                        new CouponApplyRequest(request.couponCode(), request.restaurantId().intValue(), itemTotal));
                discount = preview.discountAmount();
                freeDelivery = preview.waivesDelivery();
                couponResponse = new CartCouponValidationResponse(true, null, discount, freeDelivery);
            } catch (BadRequestException e) {
                couponResponse = new CartCouponValidationResponse(false, e.getMessage(), BigDecimal.ZERO, false);
            }
        }

        DeliveryChargeResult deliveryChargeResult = orderPricingService.computeDeliveryCharge(
                restaurant, isSelfPickup, freeDelivery, addressLatLng[0], addressLatLng[1]);

        BigDecimal amountAfterDiscount = itemTotal.subtract(discount);
        BigDecimal tax = orderPricingService.tax(amountAfterDiscount);
        BigDecimal restaurantCharge = orderPricingService.restaurantCharge(restaurant, amountAfterDiscount);
        BigDecimal payable = amountAfterDiscount.add(tax).add(restaurantCharge).add(deliveryChargeResult.amount());

        CartPricingResponse pricing = new CartPricingResponse(itemTotal, discount, tax, restaurantCharge,
                deliveryChargeResult.amount(), deliveryChargeResult.basis(), deliveryChargeResult.distanceKm(), payable);

        boolean anyUnavailable = restaurantIssue != null || itemResponses.stream().anyMatch(i -> !i.available());

        return new CartValidationResponse(
                new CartRestaurantValidationResponse(restaurantIssue == null, restaurantIssue != null ? restaurantIssue.reason() : null),
                itemResponses, couponResponse, pricing, anyUnavailable);
    }

    /**
     * Fail-fast twin of {@link #validate} - same rules, throws BadRequestException on the first
     * issue found (restaurant-level issues take priority, matching the pre-existing placeOrder
     * check order). Unlike {@code validate()}, this always has a payment mode and (for a DELIVERY
     * order) a resolved address, since {@code OrderService.placeOrder} only calls this once both
     * are known.
     */
    @Transactional(readOnly = true)
    public void assertPlaceable(Long restaurantId, List<PlaceOrderItemRequest> items, DeliveryType deliveryType,
                                 BigDecimal distanceKm, String paymentMode, Long userId) {
        Restaurant restaurant = findRestaurant(restaurantId);
        CartValidationContext context = buildContext(restaurant, toLines(items), deliveryType, distanceKm, paymentMode, userId);
        List<CartIssue> issues = evaluateAll(context);
        if (!issues.isEmpty()) {
            throw new BadRequestException(issues.get(0).reason());
        }
    }

    private List<CartIssue> evaluateAll(CartValidationContext context) {
        return rules.stream()
                .flatMap(rule -> rule.evaluate(context).stream())
                .sorted(Comparator.comparing(issue -> issue.itemId() != null))
                .toList();
    }

    private Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
    }

    private CartValidationContext buildContext(Restaurant restaurant, List<CartLine> lines, DeliveryType deliveryType,
                                                BigDecimal distanceKm, String paymentMode, Long userId) {
        Map<Long, Item> items = lines.stream()
                .map(CartLine::itemId)
                .distinct()
                .map(itemRepository::findById)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        BigDecimal rawItemTotal = lines.stream()
                .filter(line -> items.containsKey(line.itemId()))
                .map(line -> lineTotal(items.get(line.itemId()), line))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartValidationContext(restaurant, lines, items, deliveryType, distanceKm, paymentMode, userId, rawItemTotal);
    }

    private BigDecimal lineTotal(Item item, CartLine line) {
        BigDecimal addonTotal = line.selectedAddonIds() == null ? BigDecimal.ZERO
                : line.selectedAddonIds().stream()
                .map(id -> addonRepository.findById(id).map(Addon::getPrice).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return item.getPrice().add(addonTotal).multiply(BigDecimal.valueOf(line.quantity()));
    }

    /** [latitude, longitude], both null if no address given, the address doesn't exist, or (for an authenticated request) it doesn't belong to the caller. */
    private String[] resolveAddressLatLng(Long addressId, Long userId) {
        if (addressId == null) {
            return new String[]{null, null};
        }
        Address address = addressRepository.findById(addressId).orElse(null);
        if (address == null || (userId != null && !address.getUserId().equals(userId.intValue()))) {
            return new String[]{null, null};
        }
        return new String[]{address.getLatitude(), address.getLongitude()};
    }

    private static List<CartLine> toLines(List<PlaceOrderItemRequest> items) {
        return items.stream().map(i -> new CartLine(i.itemId(), i.quantity(), i.selectedAddonIds())).toList();
    }
}

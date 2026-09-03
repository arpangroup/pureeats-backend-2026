package com.pureeats.order.service.cartvalidation;

import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.repository.AddonCategoryItemRepository;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.Addon;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.DeliveryType;
import com.pureeats.order.dto.CartValidationRequest;
import com.pureeats.order.dto.CartValidationResponse;
import com.pureeats.order.dto.PlaceOrderItemRequest;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.service.OrderPricingService;
import com.pureeats.user.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartValidationServiceTest {

    private static final Long RESTAURANT_ID = 1L;
    private static final Long ITEM_ID = 10L;

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private AddonRepository addonRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private AddonCategoryItemRepository addonCategoryItemRepository;
    @Mock
    private OrderRepository orderRepository;

    private CouponService couponService;
    private OrderPricingService orderPricingService;
    private CartValidationService cartValidationService;

    @BeforeEach
    void setUp() {
        couponService = mock(CouponService.class);
        orderPricingService = new OrderPricingService();
        ReflectionTestUtils.setField(orderPricingService, "taxPercentage", BigDecimal.valueOf(5));

        // Every rule the real pipeline runs (see CartValidationRule beans), same set the Spring
        // context auto-collects in production - keeps this suite representative of the real gate.
        OrderFrequencyRule orderFrequencyRule = new OrderFrequencyRule(orderRepository);
        ReflectionTestUtils.setField(orderFrequencyRule, "windowMinutes", 10);
        ReflectionTestUtils.setField(orderFrequencyRule, "maxOrders", 3);
        lenient().when(orderRepository.findByUserIdOrderByCreatedAtDesc(anyInt())).thenReturn(List.of());

        List<CartValidationRule> rules = List.of(
                new RestaurantAvailabilityRule(), new ItemAvailabilityRule(), new ItemStockRule(),
                new AddonSelectionRule(addonRepository, addonCategoryItemRepository),
                new DeliveryRadiusRule(), new MinimumOrderAmountRule(), new PaymentMethodRule(), orderFrequencyRule);
        cartValidationService = new CartValidationService(restaurantRepository, itemRepository, addonRepository,
                addressRepository, couponService, orderPricingService, rules);
    }

    private Restaurant activeRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setIsActive(true);
        restaurant.setIsAccepted(true);
        restaurant.setIsAcceptCod(true);
        restaurant.setDeliveryChargeType("fixed");
        restaurant.setDeliveryCharges(BigDecimal.valueOf(25));
        restaurant.setRestaurantCharges(BigDecimal.valueOf(5));
        restaurant.setDeliveryRadius(BigDecimal.valueOf(10));
        restaurant.setMinOrderPrice(BigDecimal.valueOf(50));
        return restaurant;
    }

    private Item activeItem() {
        Item item = new Item();
        item.setId(ITEM_ID);
        item.setRestaurantId(RESTAURANT_ID.intValue());
        item.setName("Burger");
        item.setPrice(BigDecimal.valueOf(100));
        item.setIsActive(true);
        return item;
    }

    private CartValidationRequest requestFor(int quantity) {
        return new CartValidationRequest(RESTAURANT_ID,
                List.of(new PlaceOrderItemRequest(ITEM_ID, quantity, null)), null, null, DeliveryType.DELIVERY);
    }

    @Test
    void validate_allAvailable_computesPricingFromItemTotal() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        CartValidationResponse response = cartValidationService.validate(requestFor(2), 99L);

        assertTrue(response.restaurant().available());
        assertTrue(response.items().get(0).available());
        assertFalse(response.anyUnavailable());
        assertEquals(0, response.pricing().itemTotal().compareTo(BigDecimal.valueOf(200)));
    }

    @Test
    void validate_restaurantNotAccepting_flagsRestaurantButLeavesItemPricingIntact() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setIsAccepted(false);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        CartValidationResponse response = cartValidationService.validate(requestFor(1), 99L);

        assertFalse(response.restaurant().available());
        assertTrue(response.anyUnavailable());
        assertTrue(response.items().get(0).available(), "item availability is independent of restaurant status");
    }

    @Test
    void validate_inactiveItem_excludedFromPricingAndFlagged() {
        Item inactiveItem = activeItem();
        inactiveItem.setIsActive(false);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(inactiveItem));

        CartValidationResponse response = cartValidationService.validate(requestFor(1), 99L);

        assertFalse(response.items().get(0).available());
        assertTrue(response.anyUnavailable());
        assertEquals(0, response.pricing().itemTotal().compareTo(BigDecimal.ZERO));
    }

    @Test
    void validate_outOfStock_flagsItemWithStockReason() {
        Item outOfStock = activeItem();
        outOfStock.setStockQuantity(0);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(outOfStock));

        CartValidationResponse response = cartValidationService.validate(requestFor(1), 99L);

        assertFalse(response.items().get(0).available());
        assertEquals("Out of stock", response.items().get(0).reason());
    }

    @Test
    void validate_invalidCoupon_reportsCouponInvalidWithoutFailingWholeRequest() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(couponService.preview(any())).thenThrow(new BadRequestException("This coupon has expired"));

        CartValidationRequest request = new CartValidationRequest(RESTAURANT_ID,
                List.of(new PlaceOrderItemRequest(ITEM_ID, 1, null)), null, "EXPIRED10", DeliveryType.DELIVERY);
        CartValidationResponse response = cartValidationService.validate(request, 99L);

        assertFalse(response.coupon().valid());
        assertEquals("This coupon has expired", response.coupon().reason());
        assertTrue(response.items().get(0).available(), "an invalid coupon doesn't make items unavailable");
    }

    @Test
    void validate_belowMinimumOrderAmount_flagsRestaurantLevel() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setMinOrderPrice(BigDecimal.valueOf(500));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        CartValidationResponse response = cartValidationService.validate(requestFor(1), 99L);

        assertFalse(response.restaurant().available());
        assertTrue(response.restaurant().reason().contains("minimum order amount"));
    }

    @Test
    void validate_addonNotOfferedOnItem_flagsItem() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        Addon addon = new Addon();
        addon.setId(500L);
        addon.setName("Extra Cheese");
        addon.setAddonCategoryId(999);
        when(addonRepository.findById(500L)).thenReturn(Optional.of(addon));
        when(addonCategoryItemRepository.findByItemId(ITEM_ID)).thenReturn(List.of()); // item offers no addon categories

        CartValidationRequest request = new CartValidationRequest(RESTAURANT_ID,
                List.of(new PlaceOrderItemRequest(ITEM_ID, 1, List.of(500L))), null, null, DeliveryType.DELIVERY);
        CartValidationResponse response = cartValidationService.validate(request, 99L);

        assertFalse(response.items().get(0).available());
        assertTrue(response.items().get(0).reason().contains("not available for this item"));
    }

    @Test
    void assertPlaceable_restaurantNotAccepting_throwsOnFirstIssue() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setIsAccepted(false);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));

        assertThrows(BadRequestException.class, () -> cartValidationService.assertPlaceable(RESTAURANT_ID,
                List.of(new PlaceOrderItemRequest(ITEM_ID, 1, null)), DeliveryType.DELIVERY, null, "COD", 99L));
    }

    @Test
    void assertPlaceable_outsideDeliveryRadius_throws() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartValidationService.assertPlaceable(
                RESTAURANT_ID, List.of(new PlaceOrderItemRequest(ITEM_ID, 1, null)),
                DeliveryType.DELIVERY, BigDecimal.valueOf(25), "COD", 99L));
        assertTrue(ex.getMessage().contains("delivery radius"));
    }

    @Test
    void assertPlaceable_codOnCodRejectingRestaurant_throws() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setIsAcceptCod(false);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartValidationService.assertPlaceable(
                RESTAURANT_ID, List.of(new PlaceOrderItemRequest(ITEM_ID, 1, null)),
                DeliveryType.DELIVERY, BigDecimal.valueOf(2), "COD", 99L));
        assertTrue(ex.getMessage().contains("Cash on Delivery"));
    }

    @Test
    void assertPlaceable_tooManyRecentOrders_throws() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        com.pureeats.domain.entity.Order recent = new com.pureeats.domain.entity.Order();
        recent.setCreatedAt(java.time.LocalDateTime.now());
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(99)).thenReturn(List.of(recent, recent, recent));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartValidationService.assertPlaceable(
                RESTAURANT_ID, List.of(new PlaceOrderItemRequest(ITEM_ID, 1, null)),
                DeliveryType.DELIVERY, BigDecimal.valueOf(2), "COD", 99L));
        assertTrue(ex.getMessage().contains("orders in the last"));
    }
}

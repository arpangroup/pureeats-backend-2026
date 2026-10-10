package com.pureeats.order.service;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.DeliveryGuyRestaurant;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.User;
import com.pureeats.geo.distance.HaversineDistanceCalculator;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.DeliveryGuyRestaurantRepository;
import com.pureeats.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderDispatchServiceTest {

    @Mock private SettingValueService settings;
    @Mock private DeliveryGuyRestaurantRepository links;
    @Mock private DeliveryGuyDetailRepository details;
    @Mock private UserRepository users;

    private RiderDispatchService service;
    private Restaurant store;
    private DeliveryGuyDetail linkedRider;
    private DeliveryGuyDetail otherRider;

    @BeforeEach
    void setUp() {
        service = new RiderDispatchService(settings, links, details, users, new HaversineDistanceCalculator());
        store = new Restaurant();
        store.setId(10L);
        store.setLatitude("22.635895");
        store.setLongitude("88.353208");
        linkedRider = rider(1L, "22.6400", "88.3550");   // ~0.5 km away
        otherRider = rider(2L, "22.6370", "88.3540");    // ~0.2 km away, not linked
        lenient().when(links.findByDeliveryGuyDetailId(1L)).thenReturn(List.of(link(1L, 10L)));
        lenient().when(links.findByDeliveryGuyDetailId(2L)).thenReturn(List.of());
        lenient().when(settings.getString(eq(SettingSchemaService.RIDER_DISPATCH_RADIUS_KM), eq(null))).thenReturn("5");
        lenient().when(settings.getBoolean(eq(SettingSchemaService.RIDER_DISPATCH_FALLBACK_NEARBY), anyBoolean())).thenReturn(true);
    }

    private void mode(String mode) {
        when(settings.getString(SettingSchemaService.RIDER_DISPATCH_MODE, RiderDispatchService.LINKED_STORES)).thenReturn(mode);
    }

    private static DeliveryGuyDetail rider(long id, String lat, String lng) {
        DeliveryGuyDetail d = new DeliveryGuyDetail();
        d.setId(id);
        d.setApprovalStatus(DeliveryGuyDetail.APPROVAL_APPROVED);
        d.setIsActive(true);
        d.setIsOnline(true);
        d.setLastLat(new BigDecimal(lat));
        d.setLastLng(new BigDecimal(lng));
        return d;
    }

    private static DeliveryGuyRestaurant link(long riderId, long restaurantId) {
        DeliveryGuyRestaurant l = new DeliveryGuyRestaurant();
        l.setDeliveryGuyDetailId(riderId);
        l.setRestaurantId(restaurantId);
        return l;
    }

    @Test
    void linkedStores_onlyThePartnerLinkedToTheStoreGetsIt_evenIfAnotherIsCloser() {
        mode("LINKED_STORES");
        when(links.existsByRestaurantId(10L)).thenReturn(true);

        assertTrue(service.canReceive(linkedRider, store));
        assertFalse(service.canReceive(otherRider, store), "not linked - distance doesn't matter while the store has a linked partner");
    }

    @Test
    void linkedStores_aStoreWithNobodyLinked_fallsBackToNearbyPartners_unlessTurnedOff() {
        mode("LINKED_STORES");
        when(links.existsByRestaurantId(10L)).thenReturn(false);
        assertTrue(service.canReceive(otherRider, store));

        when(settings.getBoolean(eq(SettingSchemaService.RIDER_DISPATCH_FALLBACK_NEARBY), anyBoolean())).thenReturn(false);
        assertFalse(service.canReceive(otherRider, store));
    }

    @Test
    void nearby_usesTheRange_andNeedsAKnownLocation() {
        mode("NEARBY");
        DeliveryGuyDetail far = rider(3L, "22.80", "88.50");  // ~23 km
        lenient().when(links.findByDeliveryGuyDetailId(3L)).thenReturn(List.of());
        DeliveryGuyDetail unknown = rider(4L, "22.6", "88.3");
        unknown.setLastLat(null);
        lenient().when(links.findByDeliveryGuyDetailId(4L)).thenReturn(List.of());

        assertTrue(service.canReceive(otherRider, store));
        assertFalse(service.canReceive(far, store));
        assertFalse(service.canReceive(unknown, store));
    }

    @Test
    void all_everyPartner() {
        mode("ALL");
        assertTrue(service.canReceive(otherRider, store));
    }

    @Test
    void recipients_areOnlineApprovedActivePartnersTheRuleAllows() {
        mode("LINKED_STORES");
        when(links.existsByRestaurantId(10L)).thenReturn(true);
        DeliveryGuyDetail pending = rider(5L, "22.6400", "88.3550");
        pending.setApprovalStatus(DeliveryGuyDetail.APPROVAL_PENDING);
        when(details.findByIsOnlineTrue()).thenReturn(List.of(linkedRider, otherRider, pending));
        User u = new User();
        u.setId(101L);
        when(users.findByDeliveryGuyDetailId(1)).thenReturn(Optional.of(u));

        assertEquals(List.of(101L), service.recipientUserIds(store));
    }
}

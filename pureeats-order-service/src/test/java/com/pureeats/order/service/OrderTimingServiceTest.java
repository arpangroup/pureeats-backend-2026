package com.pureeats.order.service;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.order.repository.OrderStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimingServiceTest {

    @Mock private SettingValueService settingValueService;
    @Mock private DistanceCalculator distanceCalculator;
    @Mock private OrderStatusLogRepository orderStatusLogRepository;
    @InjectMocks private OrderTimingService service;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setLatitude("12.93");
        restaurant.setLongitude("77.62");
        lenient().when(settingValueService.getString(SettingSchemaService.DEFAULT_PREP_TIME_MINUTES, null)).thenReturn("20");
        lenient().when(settingValueService.getString(SettingSchemaService.RIDER_TO_RESTAURANT_MINUTES, null)).thenReturn("10");
    }

    @Test
    void baseEta_isPrepPlusRiderToRestaurantPlusMapTravelTime() {
        restaurant.setPreparationTime(30);
        when(distanceCalculator.etaMinutes(any(), any(), any(), any())).thenReturn(50);
        Order order = new Order();

        service.initialise(order, restaurant, false, "12.97", "77.59");

        assertEquals(30, order.getPrepareTime(), "T1 from the restaurant");
        assertEquals(10, order.getRiderToRestaurantMinutes(), "T2 from settings");
        assertEquals(50, order.getTravelMinutes(), "T3 from the map");
        assertEquals(90, order.getEtaMinutes(), "30 + 10 + 50");
    }

    @Test
    void storeWithoutItsOwnPrepTime_usesTheDefault() {
        assertEquals(20, service.prepMinutes(restaurant));
    }

    @Test
    void selfPickup_hasNoRiderOrTravelTime() {
        Order order = new Order();
        service.initialise(order, restaurant, true, null, null);
        assertEquals(20, order.getEtaMinutes());
        assertEquals(0, order.getTravelMinutes());
    }

    @Test
    void customerSlowdown_defaultsTo1_5_andIgnoresNonsense() {
        when(settingValueService.getString(SettingSchemaService.CUSTOMER_ETA_SLOWDOWN, null)).thenReturn(null, "2", "0.3", "abc");
        assertEquals(1.5, service.customerSlowdown());
        assertEquals(2.0, service.customerSlowdown());
        assertEquals(1.5, service.customerSlowdown(), "below real time isn't allowed");
        assertEquals(1.5, service.customerSlowdown());
    }
}

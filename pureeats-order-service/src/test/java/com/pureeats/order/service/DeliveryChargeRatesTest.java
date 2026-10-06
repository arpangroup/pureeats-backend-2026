package com.pureeats.order.service;

import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.order.dto.DeliveryChargeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The rates snapshot recorded with each delivery charge must be exactly what produced the amount. */
class DeliveryChargeRatesTest {

    private static OrderPricingService serviceAtDistance(String km) {
        DistanceCalculator fixedDistance = (lat1, lng1, lat2, lng2) -> new BigDecimal(km);
        return new OrderPricingService(fixedDistance, mock(AppConfigService.class), mock(com.pureeats.catalog.service.SettingValueService.class));
    }

    private static Restaurant dynamic() {
        Restaurant r = new Restaurant();
        r.setDeliveryChargeType("dynamic");
        r.setBaseDeliveryCharge(new BigDecimal("30"));
        r.setBaseDeliveryDistance(3);
        r.setExtraDeliveryCharge(new BigDecimal("10"));
        r.setExtraDeliveryDistance(1);
        return r;
    }

    @Test
    void dynamic_beyondBaseDistance_recordsRatesAndStartedExtraSteps() {
        DeliveryChargeResult result = serviceAtDistance("5.2").computeDeliveryCharge(dynamic(), false, false, "1", "1");

        assertEquals("DYNAMIC", result.basis());
        assertEquals(new BigDecimal("60"), result.amount(), "30 + ceil((5.2 - 3) / 1) * 10 = 30 + 3 * 10");
        assertEquals(new BigDecimal("30"), result.rates().baseCharge());
        assertEquals(3, result.rates().baseDistanceKm());
        assertEquals(new BigDecimal("10"), result.rates().extraCharge());
        assertEquals(1, result.rates().extraDistanceKm());
        assertEquals(3, result.rates().extraUnits());
    }

    @Test
    void dynamic_withinBaseDistance_hasNoExtraSteps() {
        DeliveryChargeResult result = serviceAtDistance("2.76").computeDeliveryCharge(dynamic(), false, false, "1", "1");
        assertEquals(new BigDecimal("30"), result.amount());
        assertEquals(0, result.rates().extraUnits());
    }

    @Test
    void fixed_recordsTheFlatCharge() {
        Restaurant r = new Restaurant();
        r.setDeliveryChargeType("fixed");
        r.setDeliveryCharges(new BigDecimal("40"));
        DeliveryChargeResult result = serviceAtDistance("7").computeDeliveryCharge(r, false, false, "1", "1");
        assertEquals("FIXED", result.basis());
        assertEquals(new BigDecimal("40"), result.rates().flatCharge());
    }

    @Test
    void selfPickup_hasNoRates() {
        assertNull(serviceAtDistance("2").computeDeliveryCharge(dynamic(), true, false, "1", "1").rates());
    }

    @Test
    void tax_usesTheAdminSetting_andFallsBackToServerConfig() {
        var settings = mock(com.pureeats.catalog.service.SettingValueService.class);
        OrderPricingService pricing = new OrderPricingService((a, b, c, d) -> BigDecimal.ONE, mock(AppConfigService.class), settings);
        org.springframework.test.util.ReflectionTestUtils.setField(pricing, "taxPercentage", BigDecimal.valueOf(5));

        org.mockito.Mockito.when(settings.getString(com.pureeats.catalog.service.SettingSchemaService.TAX_PERCENTAGE, null)).thenReturn("18");
        assertEquals(new BigDecimal("18"), pricing.taxPercentage());
        assertEquals(new BigDecimal("18.00"), pricing.tax(new BigDecimal("100")));

        org.mockito.Mockito.when(settings.getString(com.pureeats.catalog.service.SettingSchemaService.TAX_PERCENTAGE, null)).thenReturn(null);
        assertEquals(BigDecimal.valueOf(5), pricing.taxPercentage(), "unset -> server config");

        org.mockito.Mockito.when(settings.getString(com.pureeats.catalog.service.SettingSchemaService.TAX_PERCENTAGE, null)).thenReturn("abc");
        assertEquals(BigDecimal.valueOf(5), pricing.taxPercentage(), "garbage -> server config");
    }
}

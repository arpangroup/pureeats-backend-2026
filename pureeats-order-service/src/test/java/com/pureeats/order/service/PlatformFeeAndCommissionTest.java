package com.pureeats.order.service;

import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.PlatformFeeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlatformFeeAndCommissionTest {

    private SettingValueService settings;
    private AppConfigService appConfig;
    private OrderPricingService pricing;

    @BeforeEach
    void setUp() {
        settings = mock(SettingValueService.class);
        appConfig = mock(AppConfigService.class);
        // Unset settings fall through to the caller's fallback, like the real store.
        when(settings.getString(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        pricing = new OrderPricingService((a, b, c, d) -> BigDecimal.ONE, appConfig, settings);
    }

    private void set(String key, String value) {
        when(settings.getString(eq(key), any())).thenReturn(value);
    }

    @Test
    void flat_chargesTheSameAmountOnEveryOrder() {
        set(SettingSchemaService.PLATFORM_FEE_TYPE, "FLAT");
        set(SettingSchemaService.PLATFORM_FEE_AMOUNT, "5");
        assertEquals(new BigDecimal("5.00"), pricing.platformFee(new BigDecimal("150")).amount());
        assertEquals(new BigDecimal("5.00"), pricing.platformFee(new BigDecimal("1500")).amount());
    }

    @Test
    void flat_beforeTheSettingIsSaved_fallsBackToTheLegacyAppConfigFee() {
        when(appConfig.getPlatformFee()).thenReturn(new BigDecimal("3"));
        PlatformFeeResult fee = pricing.platformFee(new BigDecimal("200"));
        assertEquals(PlatformFeeResult.FLAT, fee.type());
        assertEquals(new BigDecimal("3.00"), fee.amount());
    }

    @Test
    void percentage_isAShareOfTheAmountAfterDiscount() {
        set(SettingSchemaService.PLATFORM_FEE_TYPE, "PERCENTAGE");
        set(SettingSchemaService.PLATFORM_FEE_PERCENTAGE, "2");
        PlatformFeeResult fee = pricing.platformFee(new BigDecimal("350"));
        assertEquals(new BigDecimal("7.00"), fee.amount());
        assertFalse(fee.capped());
    }

    @Test
    void percentage_respectsTheCap() {
        set(SettingSchemaService.PLATFORM_FEE_TYPE, "PERCENTAGE");
        set(SettingSchemaService.PLATFORM_FEE_PERCENTAGE, "2");
        set(SettingSchemaService.PLATFORM_FEE_MAX_AMOUNT, "25");
        PlatformFeeResult fee = pricing.platformFee(new BigDecimal("2000"));
        assertEquals(new BigDecimal("25.00"), fee.amount(), "2% of 2000 = 40, capped at 25");
        assertTrue(fee.capped());
    }

    @Test
    void commission_prefersTheStoreRate_thenTheDefault() {
        Restaurant own = new Restaurant();
        own.setCommissionRate(new BigDecimal("10"));
        assertEquals(new BigDecimal("10"), pricing.commissionPercentage(own));

        set(SettingSchemaService.DEFAULT_COMMISSION_RATE, "18");
        assertEquals(new BigDecimal("18"), pricing.commissionPercentage(new Restaurant()));
    }

    @Test
    void payout_isItemTotalMinusCommissionPlusPackaging() {
        BigDecimal commission = pricing.commission(new BigDecimal("500"), new BigDecimal("15"));
        assertEquals(new BigDecimal("75.00"), commission);
        assertEquals(new BigDecimal("445.00"), pricing.restaurantPayout(new BigDecimal("500"), commission, new BigDecimal("20")));
    }
}

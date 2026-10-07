package com.pureeats.order.service;

import com.pureeats.domain.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ShownTotalCheckTest {

    @Test
    void shownTotalMatches_orderGoesThrough() {
        assertDoesNotThrow(() -> OrderService.assertShownTotal(new BigDecimal("259.50"), new BigDecimal("259.50"), new BigDecimal("30")));
        assertDoesNotThrow(() -> OrderService.assertShownTotal(new BigDecimal("259.5"), new BigDecimal("259.50"), new BigDecimal("30")), "scale doesn't matter");
    }

    @Test
    void olderAppWithoutAShownTotal_isNotChecked() {
        assertDoesNotThrow(() -> OrderService.assertShownTotal(null, new BigDecimal("259.50"), new BigDecimal("30")));
    }

    @Test
    void deliveryChargeHigherThanShown_isRefusedWithTheNewAmounts() {
        // App showed a flat ₹20 delivery estimate; the store's distance-based rate is ₹30.
        ConflictException ex = assertThrows(ConflictException.class,
                () -> OrderService.assertShownTotal(new BigDecimal("249.50"), new BigDecimal("259.50"), new BigDecimal("30")));
        assertTrue(ex.getMessage().contains("₹259.50"));
        assertTrue(ex.getMessage().contains("delivery charge ₹30.00"));
        assertTrue(ex.getMessage().contains("₹249.50 shown"));
    }
}

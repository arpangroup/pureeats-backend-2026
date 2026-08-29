package com.pureeats.order.service;

import com.pureeats.domain.entity.RestaurantEarning;
import com.pureeats.domain.entity.RestaurantPayout;
import com.pureeats.order.repository.RestaurantEarningRepository;
import com.pureeats.order.repository.RestaurantPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Restaurant settlement ledger - separate from {@link WalletService}, matching the legacy schema. */
@Service
@RequiredArgsConstructor
public class RestaurantPayoutService {

    private final RestaurantEarningRepository restaurantEarningRepository;
    private final RestaurantPayoutRepository restaurantPayoutRepository;

    @Transactional
    public void recordEarning(Integer restaurantId, BigDecimal amount) {
        RestaurantEarning earning = new RestaurantEarning();
        earning.setRestaurantId(restaurantId);
        earning.setAmount(amount);
        earning.setIsRequested(false);
        earning.setIsProcessed(false);
        earning.setCreatedAt(LocalDateTime.now());
        earning.setUpdatedAt(LocalDateTime.now());
        restaurantEarningRepository.save(earning);
    }

    @Transactional(readOnly = true)
    public BigDecimal getUnsettledBalance(Integer restaurantId) {
        return restaurantEarningRepository.findByRestaurantIdAndIsProcessedFalse(restaurantId).stream()
                .map(RestaurantEarning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void requestPayout(Integer restaurantId) {
        List<RestaurantEarning> unsettled = restaurantEarningRepository.findByRestaurantIdAndIsProcessedFalse(restaurantId);
        BigDecimal total = unsettled.stream().map(RestaurantEarning::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        RestaurantPayout payout = new RestaurantPayout();
        payout.setRestaurantId(restaurantId);
        payout.setRestaurantEarningId(unsettled.get(0).getId().intValue());
        payout.setAmount(total);
        payout.setStatus("PENDING");
        payout.setCreatedAt(LocalDateTime.now());
        payout.setUpdatedAt(LocalDateTime.now());
        payout = restaurantPayoutRepository.save(payout);

        for (RestaurantEarning earning : unsettled) {
            earning.setIsRequested(true);
            earning.setRestaurantPayoutId(payout.getId().intValue());
        }
        restaurantEarningRepository.saveAll(unsettled);
    }
}

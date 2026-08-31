package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantEarning;
import com.pureeats.domain.entity.RestaurantPayout;
import com.pureeats.order.dto.AdminRestaurantPayoutResponse;
import com.pureeats.order.repository.RestaurantEarningRepository;
import com.pureeats.order.repository.RestaurantPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Restaurant settlement ledger - separate from {@link WalletService}, matching the legacy schema. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantPayoutService {

    private final RestaurantEarningRepository restaurantEarningRepository;
    private final RestaurantPayoutRepository restaurantPayoutRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public void recordEarning(Integer restaurantId, BigDecimal amount) {
        log.debug("Recording earning {} for restaurant {}", amount, restaurantId);
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
            log.warn("Rejected payout request for restaurant {}: no unsettled earnings", restaurantId);
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
        log.info("Payout {} requested for restaurant {}: amount {} across {} earning entries",
                payout.getId(), restaurantId, total, unsettled.size());
    }

    @Transactional(readOnly = true)
    public AdminRestaurantPayoutResponse getById(Long id) {
        return toResponse(restaurantPayoutRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payout not found: {}", id);
                    return new ResourceNotFoundException("Payout not found: " + id);
                }));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantPayoutResponse> listPaged(Pageable pageable) {
        Page<RestaurantPayout> page = restaurantPayoutRepository.findAllByOrderByCreatedAtDesc(pageable);
        List<AdminRestaurantPayoutResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public AdminRestaurantPayoutResponse updateStatus(Long id, String wireStatus) {
        RestaurantPayout payout = restaurantPayoutRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payout not found: {}", id);
                    return new ResourceNotFoundException("Payout not found: " + id);
                });
        String previousStatus = payout.getStatus();
        payout.setStatus(wireStatus.toUpperCase());
        payout.setUpdatedAt(LocalDateTime.now());
        restaurantPayoutRepository.save(payout);
        log.info("Payout {} status updated {} -> {}", id, previousStatus, payout.getStatus());

        if ("PAID".equals(payout.getStatus())) {
            List<RestaurantEarning> earnings = restaurantEarningRepository.findByRestaurantPayoutId(payout.getId().intValue());
            earnings.forEach(e -> e.setIsProcessed(true));
            restaurantEarningRepository.saveAll(earnings);
            log.debug("Marked {} earning entries as processed for payout {}", earnings.size(), id);
        }
        return toResponse(payout);
    }

    private AdminRestaurantPayoutResponse toResponse(RestaurantPayout p) {
        String restaurantName = restaurantRepository.findById(p.getRestaurantId().longValue())
                .map(Restaurant::getName).orElse("Unknown");
        return new AdminRestaurantPayoutResponse(p.getId(), p.getRestaurantId().longValue(), restaurantName,
                p.getRestaurantEarningId().longValue(), p.getAmount(), p.getStatus().toLowerCase(),
                p.getTransactionMode(), p.getTransactionId(), p.getMessage(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

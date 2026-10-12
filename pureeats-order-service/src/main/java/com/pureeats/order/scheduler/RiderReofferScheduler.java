package com.pureeats.order.scheduler;

import com.pureeats.order.service.RiderOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: offer again the orders no delivery partner has taken (see RiderOfferService#reofferUnassigned). How
 * often each order repeats, and for how long, is set in Settings -> Delivery Application -> New order alerts.
 */
@Component
@RequiredArgsConstructor
public class RiderReofferScheduler {

    private final RiderOfferService riderOfferService;

    @Scheduled(fixedDelayString = "${pureeats.delivery.reoffer-check-interval-ms:60000}",
            initialDelayString = "${pureeats.delivery.reoffer-check-interval-ms:60000}")
    public void run() {
        riderOfferService.reofferUnassigned();
    }
}

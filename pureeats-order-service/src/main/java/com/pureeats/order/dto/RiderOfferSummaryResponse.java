package com.pureeats.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * For the admin's order page: which delivery partners were alerted about this order (per round), who dismissed it,
 * and when the next alert is due.
 *
 * @param state       WAITING (re-alerts will continue), ASSIGNED, STOPPED_MAX (max alerts reached), DISABLED
 *                    (re-alerts switched off), NOT_OFFERED (not offered to partners - e.g. self-pickup, or the
 *                    restaurant hasn't accepted yet) or ENDED (no longer waiting - picked up, delivered, cancelled...)
 * @param nextOfferAt when the scheduler will alert again (it checks once a minute, so up to a minute later); null when it won't
 */
public record RiderOfferSummaryResponse(
        String state,
        int offerCount,
        LocalDateTime lastOfferAt,
        LocalDateTime nextOfferAt,
        int intervalMinutes,
        /** UNTIL_ASSIGNED or LIMITED */
        String mode,
        int maxOffers,
        /** Who gets alerts: LINKED_STORES, NEARBY or ALL. */
        String dispatchMode,
        LocalDateTime noRiderAlertedAt,
        List<Round> rounds,
        List<Dismissal> dismissals
) {
    public record Partner(Long userId, String name, String phone) {
    }

    /** notifiedAt is null for an earlier round that reached nobody (its time wasn't kept). */
    public record Round(int round, LocalDateTime notifiedAt, List<Partner> partners) {
    }

    public record Dismissal(Partner partner, int round, LocalDateTime dismissedAt) {
    }
}

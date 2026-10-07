package com.pureeats.user.security;

import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.security.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a signed-in user may still use the API. A JWT stays valid until it expires, so
 * without this a user an admin blocked kept working on their current token (and every app refreshes
 * silently). Checked on every authenticated request by {@link JwtAuthenticationFilter}; the answer is
 * cached briefly per user and dropped as soon as an admin changes the account ({@link #onAccountChanged}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAccessGuard {

    /** Error code the apps sign out on. */
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final SessionService sessionService;

    private record Decision(Optional<String> denial, Instant expiresAt) {
    }

    private final Map<Long, Decision> cache = new ConcurrentHashMap<>();

    /** Empty when the user may continue; otherwise the message to show them. */
    public Optional<String> denialFor(Long userId) {
        if (userId == null) return Optional.empty();
        Decision cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.denial();
        Optional<String> denial = evaluate(userId);
        cache.put(userId, new Decision(denial, Instant.now().plus(CACHE_TTL)));
        return denial;
    }

    /**
     * Call whenever an admin changes a user's access. Drops the cached decision and, if the user can no
     * longer use the app, revokes every refresh session so no device can quietly sign back in.
     */
    public void onAccountChanged(Long userId) {
        if (userId == null) return;
        cache.remove(userId);
        if (evaluate(userId).isPresent()) {
            sessionService.revokeAllForUser(userId);
            log.info("User {} can no longer use the app - all sessions revoked", userId);
        }
    }

    private Optional<String> evaluate(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        // Deleting an account is a soft delete (status DELETED, handled below); no row means nothing to decide here.
        if (user == null) return Optional.empty();
        if (User.STATUS_INACTIVE.equalsIgnoreCase(user.getIsActive())) {
            return Optional.of("Your account has been blocked. Please contact support.");
        }
        AccountStatus status = user.getAccountStatus() != null ? user.getAccountStatus() : AccountStatus.ACTIVE;
        if (status == AccountStatus.BLOCKED || status == AccountStatus.DISABLED || status == AccountStatus.DELETED) {
            return Optional.of(user.getLockReason() != null ? user.getLockReason() : "Your account has been blocked. Please contact support.");
        }
        return riderDenial(user);
    }

    /** A delivery partner deactivated from Delivery partners (their partner profile) is blocked too. */
    public Optional<String> riderDenial(User user) {
        if (user.getDeliveryGuyDetailId() == null) return Optional.empty();
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue()).orElse(null);
        if (detail != null && Boolean.FALSE.equals(detail.getIsActive())) {
            return Optional.of("Your delivery partner account has been deactivated. Please contact support.");
        }
        return Optional.empty();
    }
}

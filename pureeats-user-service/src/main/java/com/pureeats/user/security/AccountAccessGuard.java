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
    /** Error code for a token issued before the user signed out of all devices. */
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final SessionService sessionService;
    private final AccountMessages accountMessages;

    private record Decision(Optional<String> denial, Instant expiresAt) {
    }

    private final Map<Long, Decision> cache = new ConcurrentHashMap<>();
    /**
     * Per user: tokens issued before this instant are no longer accepted. In memory is enough - refresh
     * sessions are revoked in the database, so after a restart a stale access token can only live out its
     * short remaining lifetime (access tokens last 15 minutes).
     */
    private final Map<Long, Instant> signedOutAt = new ConcurrentHashMap<>();

    /** True when this token was issued before the user signed out of every device. */
    public boolean isRevoked(Long userId, Instant tokenIssuedAt) {
        if (userId == null || tokenIssuedAt == null) return false;
        Instant cutoff = signedOutAt.get(userId);
        return cutoff != null && tokenIssuedAt.isBefore(cutoff);
    }

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
            signOutEverywhere(userId);
            log.info("User {} can no longer use the app - all sessions revoked", userId);
        }
    }

    /**
     * Signs the user out of every device now - "log out of all devices", or when their delivery partner profile
     * is removed. Every token issued until now is rejected on its next request ({@link #isRevoked}) and all
     * refresh sessions are revoked, so no app can sign back in without a fresh login.
     */
    public void signOutEverywhere(Long userId) {
        if (userId == null) return;
        cache.remove(userId);
        // JWT issued-at has second precision - a token from this very second counts as "before".
        signedOutAt.put(userId, Instant.now().plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        sessionService.revokeAllForUser(userId);
        log.info("Signed user {} out of every device", userId);
    }

    private Optional<String> evaluate(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        // Deleting an account is a soft delete (status DELETED, handled below); no row means nothing to decide here.
        if (user == null) return Optional.empty();
        if (user.getAccountStatus() != AccountStatus.DELETED && User.STATUS_INACTIVE.equalsIgnoreCase(user.getIsActive())) {
            return Optional.of(accountMessages.blocked());
        }
        AccountStatus status = user.getAccountStatus() != null ? user.getAccountStatus() : AccountStatus.ACTIVE;
        if (status == AccountStatus.DELETED) {
            return Optional.of(accountMessages.deleted());
        }
        if (status == AccountStatus.BLOCKED || status == AccountStatus.DISABLED) {
            return Optional.of(user.getLockReason() != null ? user.getLockReason() : accountMessages.blocked());
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

package com.pureeats.user.security.session;

import com.pureeats.domain.common.exception.ApiException;
import com.pureeats.domain.common.exception.UnauthorizedException;
import com.pureeats.user.entity.UserSession;
import com.pureeats.user.config.AuthSecurityProperties;
import com.pureeats.user.repository.UserSessionRepository;
import com.pureeats.user.security.metadata.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens. Only a SHA-256 hash of each token is ever
 * persisted (see {@link UserSession#getRefreshTokenHash()}) - a database dump alone can never be
 * replayed into a live session. Rotation additionally detects reuse of an already-rotated-out
 * token (a strong signal of token theft) and, on detection, revokes every session for that user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionRepository userSessionRepository;
    private final AuthSecurityProperties properties;

    public record IssuedSession(String sessionId, String rawRefreshToken, LocalDateTime expiresAt) {
    }

    public record RotatedSession(Long userId, String sessionId, String rawRefreshToken, LocalDateTime expiresAt) {
    }

    @Transactional
    public IssuedSession createSession(Long userId, RequestMetadata metadata) {
        String rawToken = generateRawToken();
        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setDeviceId(metadata.deviceId());
        session.setRefreshTokenHash(hash(rawToken));
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(properties.getSession().getRefreshTokenExpiryDays()));
        session.setIpAddress(metadata.ipAddress());
        session.setUserAgent(metadata.userAgent());
        userSessionRepository.save(session);
        return new IssuedSession(session.getSessionId(), rawToken, session.getExpiresAt());
    }

    /**
     * {@code REQUIRES_NEW} + {@code noRollbackFor}: on reuse detection this revokes every session
     * for the user before throwing - that revocation must survive regardless of what the caller's
     * transaction later does with the exception (see the equivalent note on
     * {@code OtpChallengeService.verify}/{@code resend} for why a default-rollback would silently
     * undo it).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public RotatedSession rotate(String rawRefreshToken, RequestMetadata metadata) {
        UserSession session = userSessionRepository.findByRefreshTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token"));

        if (session.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for userId={} sessionId={} - revoking all sessions", session.getUserId(), session.getSessionId());
            userSessionRepository.revokeAllForUser(session.getUserId(), LocalDateTime.now());
            throw new UnauthorizedException("REFRESH_TOKEN_REUSE_DETECTED", "This session has been revoked for your security. Please log in again.");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Your session has expired. Please log in again.");
        }

        session.setRevokedAt(LocalDateTime.now());
        session.setLastUsedAt(LocalDateTime.now());
        userSessionRepository.save(session);

        IssuedSession next = createSession(session.getUserId(), metadata);
        return new RotatedSession(session.getUserId(), next.sessionId(), next.rawRefreshToken(), next.expiresAt());
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        userSessionRepository.findByRefreshTokenHash(hash(rawRefreshToken))
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    userSessionRepository.save(session);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        userSessionRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

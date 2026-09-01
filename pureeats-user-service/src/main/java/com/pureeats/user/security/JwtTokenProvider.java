package com.pureeats.user.security;

import com.pureeats.domain.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates the JWT that carries reusable identity/role claims
 * (userId, name, email, phone, role, deliveryGuyDetailId) for the frontend
 * and any internal caller to read without a further lookup.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PHONE = "phone";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_DELIVERY_GUY_DETAIL_ID = "deliveryGuyDetailId";

    @Value("${pureeats.jwt.secret}")
    private String secret;

    @Value("${pureeats.jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AuthenticatedUser user) {
        return generateToken(user, expirationMs);
    }

    /** Same claims as {@link #generateToken(AuthenticatedUser)}, but with a caller-chosen lifetime - used by the
     * OTP-challenge flow's short-lived (default 15 min) access tokens instead of the legacy 24h default. */
    public String generateToken(AuthenticatedUser user, long expirationMsOverride) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMsOverride);

        var builder = Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim(CLAIM_NAME, user.name())
                //.claim(CLAIM_EMAIL, user.email()) // Don't expose PI data
                //.claim(CLAIM_PHONE, user.phone()) // Don't expose PI data
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key());

        if (user.deliveryGuyDetailId() != null) {
            builder.claim(CLAIM_DELIVERY_GUY_DETAIL_ID, user.deliveryGuyDetailId());
        }
        return builder.compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * @return the parsed principal, or {@code null} if the token is missing, malformed,
     * expired, or signed with a different key.
     */
    public AuthenticatedUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long deliveryGuyDetailId = claims.get(CLAIM_DELIVERY_GUY_DETAIL_ID, Long.class);
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_NAME, String.class),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_PHONE, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    deliveryGuyDetailId
            );
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

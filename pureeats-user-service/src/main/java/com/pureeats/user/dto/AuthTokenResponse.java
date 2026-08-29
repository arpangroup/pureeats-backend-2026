package com.pureeats.user.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}

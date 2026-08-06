package com.medora.auth.api.response;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
}

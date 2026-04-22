package com.medora.auth.api.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        LoggedInUser user
) {
    public record LoggedInUser(
            String userId,
            String displayName,
            String mobileMasked,
            java.util.List<String> roles
    ) {
    }
}

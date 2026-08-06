package com.medora.auth.api.response;

public record RegisterResponse(
        String userId,
        String displayName
) {
}

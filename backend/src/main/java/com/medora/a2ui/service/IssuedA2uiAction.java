package com.medora.a2ui.service;

import java.time.OffsetDateTime;

public record IssuedA2uiAction(String actionId, String surfaceId, String confirmationToken, OffsetDateTime expiresAt) {
}

package com.medora.admin.api.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminMemoryProfileItem(
        String memoryId,
        String memoryType,
        String memoryKey,
        String summary,
        String valueJson,
        BigDecimal confidence,
        String source,
        String status,
        OffsetDateTime lastConfirmedAt,
        OffsetDateTime lastAccessedAt,
        OffsetDateTime expiresAt
) {
}

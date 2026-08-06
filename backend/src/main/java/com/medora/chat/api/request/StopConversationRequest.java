package com.medora.chat.api.request;

import jakarta.validation.constraints.Size;

public record StopConversationRequest(
        @Size(max = 128, message = "traceId length must be less than 128")
        String traceId
) {
}

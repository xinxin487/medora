package com.medora.chat.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatStreamRequest(
        @NotBlank(message = "message is required")
        @Size(max = 4000, message = "message length must be less than 4000")
        String message,

        @Size(max = 128, message = "clientMessageId length must be less than 128")
        String clientMessageId,

        @Valid
        ChatStreamContext context
) {
}

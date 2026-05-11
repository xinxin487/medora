package com.medora.chat.api.request;

import jakarta.validation.constraints.Size;

public record ChatStreamContext(
        @Size(max = 32, message = "sceneType length must be less than 32")
        String sceneType
) {
}

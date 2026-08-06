package com.medora.chat.api.request;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 128, message = "title length must be less than 128")
        String title,

        @Size(max = 32, message = "sceneType length must be less than 32")
        String sceneType
) {
}

package com.medora.a2ui.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** Only opaque server-issued identifiers and the one-time token are accepted from the UI. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record A2uiActionRequest(
        @NotBlank String surfaceId,
        @NotBlank String actionId,
        @NotBlank String confirmationToken
) {
}

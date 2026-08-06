package com.medora.appointment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
        @NotBlank(message = "slotId is required")
        String slotId,

        @Size(max = 255, message = "remark must not exceed 255 characters")
        String remark
) {
}

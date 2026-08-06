package com.medora.appointment.api.request;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @Size(max = 255, message = "cancelReason must not exceed 255 characters")
        String cancelReason
) {
}

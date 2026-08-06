package com.medora.appointment.api.response;

import java.time.OffsetDateTime;

public record AppointmentCancelResponse(
        String appointmentId,
        String status,
        OffsetDateTime cancelledAt,
        String cancelReason
) {
}

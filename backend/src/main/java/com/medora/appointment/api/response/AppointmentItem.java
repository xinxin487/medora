package com.medora.appointment.api.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AppointmentItem(
        String appointmentId,
        String appointmentNo,
        String status,
        OffsetDateTime bookedAt,
        String scheduleDate,
        String periodCode,
        String departmentName,
        String doctorName,
        BigDecimal fee
) {
}

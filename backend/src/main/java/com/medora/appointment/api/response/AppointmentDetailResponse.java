package com.medora.appointment.api.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AppointmentDetailResponse(
        String appointmentId,
        String appointmentNo,
        String status,
        OffsetDateTime bookedAt,
        OffsetDateTime cancelledAt,
        String cancelReason,
        String remark,
        Schedule schedule,
        Department department,
        Doctor doctor,
        BigDecimal fee
) {
    public record Schedule(
            String slotId,
            String scheduleDate,
            String periodCode,
            String startTime,
            String endTime
    ) {
    }

    public record Department(
            String departmentId,
            String name
    ) {
    }

    public record Doctor(
            String doctorId,
            String name,
            String title
    ) {
    }
}

package com.medora.appointment.api.response;

import java.time.OffsetDateTime;

public record AppointmentResponse(
        String appointmentId,
        String appointmentNo,
        String status,
        OffsetDateTime bookedAt,
        Schedule schedule,
        Department department,
        Doctor doctor
) {
    public record Schedule(
            String slotId,
            String scheduleDate,
            String periodCode
    ) {
    }

    public record Department(
            String departmentId,
            String name
    ) {
    }

    public record Doctor(
            String doctorId,
            String name
    ) {
    }
}

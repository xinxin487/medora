package com.medora.appointment.api.query;

public record AppointmentListQuery(
        String status,
        String scheduleDateFrom,
        String scheduleDateTo,
        Integer pageNo,
        Integer pageSize
) {
}

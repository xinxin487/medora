package com.medora.appointment.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AppointmentListQuery(
        @Size(max = 20) String status,
        @Size(max = 10) String scheduleDateFrom,
        @Size(max = 10) String scheduleDateTo,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize
) {
}

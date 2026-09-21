package com.medora.appointment.hold;

import java.time.OffsetDateTime;

public record PreparedAppointment(String holdId, String slotId, OffsetDateTime expiresAt, boolean reused) {
}

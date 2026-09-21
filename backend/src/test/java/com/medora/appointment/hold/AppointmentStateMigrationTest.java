package com.medora.appointment.hold;

import com.medora.masterdata.domain.ScheduleSlotEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStateMigrationTest {

    @Test
    void holdConfirmationAndReleasePreserveSlotQuotaInvariant() {
        ScheduleSlotEntity slot = slot(10, 8, 2, "OPEN");

        slot.holdOne();
        slot.confirmHeldOne();
        slot.holdOne();
        slot.releaseHeldOne();

        assertThat(slot.getAvailableQuota()).isEqualTo(7);
        assertThat(slot.getReservedQuota()).isEqualTo(2);
        assertThat(slot.getAvailableQuota() + slot.getReservedQuota() + 1).isEqualTo(slot.getTotalQuota());
    }

    @Test
    void holdAllowsOnlyNamedPendingTransitions() {
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1", OffsetDateTime.parse("2026-09-21T14:00:00Z"));

        hold.confirm();

        assertThat(hold.getStatus()).isEqualTo("CONFIRMED");
        assertThatThrownBy(hold::expire).isInstanceOf(IllegalStateException.class);
    }

    private ScheduleSlotEntity slot(int total, int available, int reserved, String status) {
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "totalQuota", total);
        ReflectionTestUtils.setField(slot, "availableQuota", available);
        ReflectionTestUtils.setField(slot, "reservedQuota", reserved);
        ReflectionTestUtils.setField(slot, "status", status);
        return slot;
    }
}

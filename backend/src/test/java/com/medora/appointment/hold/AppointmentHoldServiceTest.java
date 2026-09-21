package com.medora.appointment.hold;

import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentHoldServiceTest {

    @Test
    void returnsTheExistingHoldForTheSameUserAndIdempotencyKey() {
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        AppointmentHoldEntity existing = AppointmentHoldEntity.pending(7L, 11L, "idem-1", Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(existing, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000020"));
        when(holds.findByUserIdAndIdempotencyKey(7L, "idem-1")).thenReturn(Optional.of(existing));
        AppointmentHoldService service = new AppointmentHoldService(holds, slots, failingLock(), fixedClock());

        PreparedAppointment prepared = service.prepare(7L, UUID.randomUUID().toString(), "idem-1", "AGENT");

        assertThat(prepared.holdId()).isEqualTo("00000000-0000-0000-0000-000000000020");
        assertThat(prepared.reused()).isTrue();
    }

    @Test
    void fallsBackToDatabaseRowLockAndMovesQuotaOnceWhenRedisFails() {
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        UUID slotId = UUID.randomUUID();
        ScheduleSlotEntity slot = slot(slotId);
        when(holds.findByUserIdAndIdempotencyKey(7L, "idem-2")).thenReturn(Optional.empty());
        when(slots.findByPublicIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(holds.save(any(AppointmentHoldEntity.class))).thenAnswer(invocation -> {
            AppointmentHoldEntity hold = invocation.getArgument(0);
            ReflectionTestUtils.setField(hold, "publicId", UUID.randomUUID());
            return hold;
        });
        AppointmentHoldService service = new AppointmentHoldService(holds, slots, failingLock(), fixedClock());

        PreparedAppointment prepared = service.prepare(7L, slotId.toString(), "idem-2", "AGENT");

        assertThat(prepared.reused()).isFalse();
        assertThat(slot.getAvailableQuota()).isEqualTo(1);
        assertThat(slot.getReservedQuota()).isEqualTo(1);
        verify(slots).findByPublicIdForUpdate(slotId);
        verify(holds).save(any(AppointmentHoldEntity.class));
    }

    private SlotLock failingLock() { return slotId -> { throw new IllegalStateException("redis unavailable"); }; }
    private Clock fixedClock() { return Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC); }
    private ScheduleSlotEntity slot(UUID publicId) {
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "id", 11L);
        ReflectionTestUtils.setField(slot, "publicId", publicId);
        ReflectionTestUtils.setField(slot, "totalQuota", 2);
        ReflectionTestUtils.setField(slot, "availableQuota", 2);
        ReflectionTestUtils.setField(slot, "reservedQuota", 0);
        ReflectionTestUtils.setField(slot, "status", "OPEN");
        return slot;
    }
}

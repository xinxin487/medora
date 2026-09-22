package com.medora.appointment.hold;

import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import com.medora.appointment.waitlist.WaitlistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class AppointmentHoldExpiryJobTest {

    @Test
    void databaseScanExpiresOnceAndRestoresTheHeldQuotaWhenRedisIsEmpty() {
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        HoldExpiryIndex expiryIndex = mock(HoldExpiryIndex.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1",
                Instant.parse("2026-09-21T13:59:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000088"));
        ScheduleSlotEntity slot = heldSlot();
        when(holds.findPendingExpiredForUpdate(Instant.parse("2026-09-21T14:00:00Z").atOffset(ZoneOffset.UTC)))
                .thenReturn(List.of(hold));
        when(slots.findByIdForUpdate(19L)).thenReturn(java.util.Optional.of(slot));
        AppointmentHoldExpiryJob job = new AppointmentHoldExpiryJob(holds, slots, expiryIndex, events, fixedClock(), mock(WaitlistEntryRepository.class));

        job.expireOverdue();
        job.expireOverdue();

        assertThat(hold.getStatus()).isEqualTo("EXPIRED");
        assertThat(slot.getAvailableQuota()).isEqualTo(1);
        assertThat(slot.getReservedQuota()).isZero();
        verify(events, times(1)).publishEvent(org.mockito.ArgumentMatchers.any(SlotReleased.class));
        verify(expiryIndex, times(1)).remove(hold.getPublicId().toString());
    }

    @Test
    void expiresAnOfferedWaitlistHoldAndPublishesTheReleasedSlotForTheNextCandidate() {
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        HoldExpiryIndex expiryIndex = mock(HoldExpiryIndex.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        WaitlistEntryRepository waitlistEntries = mock(WaitlistEntryRepository.class);
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "WAIT-30",
                Instant.parse("2026-09-21T13:59:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "id", 88L);
        ReflectionTestUtils.setField(hold, "publicId", UUID.randomUUID());
        var offered = com.medora.appointment.waitlist.WaitlistEntryEntity.waiting(7L, 19L, 0,
                Instant.parse("2026-09-21T13:58:00Z").atOffset(ZoneOffset.UTC));
        offered.offer(88L);
        when(holds.findPendingExpiredForUpdate(any())).thenReturn(List.of(hold));
        when(slots.findByIdForUpdate(19L)).thenReturn(java.util.Optional.of(heldSlot()));
        when(waitlistEntries.findByOfferedHoldId(88L)).thenReturn(java.util.Optional.of(offered));
        AppointmentHoldExpiryJob job = new AppointmentHoldExpiryJob(holds, slots, expiryIndex, events, fixedClock(), waitlistEntries);

        job.expireOverdue();

        assertThat(offered.getStatus()).isEqualTo("EXPIRED");
        verify(events).publishEvent(new SlotReleased(19L));
    }

    private Clock fixedClock() { return Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC); }

    private ScheduleSlotEntity heldSlot() {
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "totalQuota", 1);
        ReflectionTestUtils.setField(slot, "id", 19L);
        ReflectionTestUtils.setField(slot, "availableQuota", 0);
        ReflectionTestUtils.setField(slot, "reservedQuota", 1);
        ReflectionTestUtils.setField(slot, "status", "FULL");
        return slot;
    }
}

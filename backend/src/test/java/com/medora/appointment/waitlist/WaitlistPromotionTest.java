package com.medora.appointment.waitlist;

import com.medora.appointment.hold.SlotReleased;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.hold.AppointmentHoldService;
import com.medora.a2ui.service.A2uiActionService;
import com.medora.a2ui.service.AppointmentSurfaceFactory;
import com.medora.a2ui.service.IssuedA2uiAction;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WaitlistPromotionTest {
    @Test
    void anExpiredOfferTransitionsOutOfActiveMembership() {
        WaitlistEntryEntity entry = WaitlistEntryEntity.waiting(7L, 19L, 0, Instant.parse("2026-09-21T14:00:00Z").atOffset(ZoneOffset.UTC));
        entry.offer(55L);
        entry.expireOffer();
        assertThat(entry.getStatus()).isEqualTo("EXPIRED");
    }
    @Test
    void skipsCancelledAndStaleEntriesThenOffersTheNextFifoCandidate() {
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        WaitlistIndex index = mock(WaitlistIndex.class);
        WaitlistEntryEntity cancelled = WaitlistEntryEntity.waiting(1L, 19L, 0, Instant.parse("2026-09-21T13:58:00Z").atOffset(ZoneOffset.UTC));
        cancelled.cancel();
        WaitlistEntryEntity candidate = WaitlistEntryEntity.waiting(3L, 19L, 0, Instant.parse("2026-09-21T13:59:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(candidate, "id", 30L);
        when(index.popNext(19L)).thenReturn(Optional.of("stale"), Optional.of("30"), Optional.empty());
        when(entries.findByIdForUpdate(30L)).thenReturn(Optional.of(candidate));
        AppointmentHoldService holds = mock(AppointmentHoldService.class);
        AppointmentHoldRepository holdRepository = mock(AppointmentHoldRepository.class);
        A2uiActionService actions = mock(A2uiActionService.class);
        AppointmentSurfaceFactory surfaces = mock(AppointmentSurfaceFactory.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        UUID slotPublicId = UUID.randomUUID();
        ReflectionTestUtils.setField(slot, "publicId", slotPublicId);
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(3L, 19L, "WAIT-30", Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "id", 55L);
        when(slots.findById(19L)).thenReturn(Optional.of(slot));
        when(holdRepository.findByUserIdAndIdempotencyKey(3L, "WAIT-30")).thenReturn(Optional.of(hold));
        when(actions.issue(hold, "waitlist-30")).thenReturn(new IssuedA2uiAction(UUID.randomUUID().toString(), "waitlist-30", "token",
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC)));
        WaitlistPromotionHandler handler = new WaitlistPromotionHandler(entries, index, slots, holds, holdRepository, actions, surfaces, fixedClock());

        handler.onSlotReleased(new SlotReleased(19L));

        assertThat(candidate.getStatus()).isEqualTo("OFFERED");
        assertThat(candidate.getOfferedHoldId()).isEqualTo(55L);
        verify(holds).prepare(3L, slotPublicId.toString(), "WAIT-30", "WAITLIST");
        verify(actions).issue(hold, "waitlist-30");
        verify(surfaces).create(eq(hold), any(IssuedA2uiAction.class));
        verify(index).remove(19L, "stale");
    }
    @Test
    void rebuildRestoresOnlyWaitingEntriesInPriorityThenFifoOrder() {
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        WaitlistIndex index = mock(WaitlistIndex.class);
        WaitlistEntryEntity entry = WaitlistEntryEntity.waiting(7L, 19L, 2, Instant.parse("2026-09-21T14:00:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(entry, "id", 40L);
        when(entries.findByStatusOrderByPriorityDescJoinedAtAsc("WAITING")).thenReturn(List.of(entry));
        WaitlistRebuildJob job = new WaitlistRebuildJob(entries, index);

        job.rebuild();

        verify(index).add(eq(19L), eq("40"), anyDouble());
    }

    @Test
    void requeuesClaimedCandidateWhenOfferCreationFails() {
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        WaitlistIndex index = mock(WaitlistIndex.class);
        WaitlistEntryEntity candidate = WaitlistEntryEntity.waiting(3L, 19L, 2,
                Instant.parse("2026-09-21T13:59:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(candidate, "id", 30L);
        when(index.popNext(19L)).thenReturn(Optional.of("30"));
        when(entries.findByIdForUpdate(30L)).thenReturn(Optional.of(candidate));
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "publicId", UUID.randomUUID());
        when(slots.findById(19L)).thenReturn(Optional.of(slot));
        AppointmentHoldService holds = mock(AppointmentHoldService.class);
        doThrow(new IllegalStateException("Redis unavailable")).when(holds)
                .prepare(3L, slot.getPublicId().toString(), "WAIT-30", "WAITLIST");
        WaitlistPromotionHandler handler = new WaitlistPromotionHandler(entries, index, slots, holds,
                mock(AppointmentHoldRepository.class), mock(A2uiActionService.class),
                mock(AppointmentSurfaceFactory.class), fixedClock());

        handler.onSlotReleased(new SlotReleased(19L));

        assertThat(candidate.getStatus()).isEqualTo("WAITING");
        verify(index).add(eq(19L), eq("30"), anyDouble());
    }
    private Clock fixedClock() { return Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC); }
}

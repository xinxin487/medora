package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.service.AppointmentCommandService;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import com.medora.appointment.waitlist.WaitlistEntryEntity;
import com.medora.appointment.waitlist.WaitlistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class A2uiActionServiceTest {

    @Test
    void issuesAnOpaqueConfirmationTokenBoundToHoldUserAndSurface() {
        A2uiActionRepository repository = mock(A2uiActionRepository.class);
        when(repository.save(any(A2uiActionEntity.class))).thenAnswer(invocation -> {
            A2uiActionEntity action = invocation.getArgument(0);
            ReflectionTestUtils.setField(action, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000021"));
            return action;
        });
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1", Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "id", 88L);
        A2uiActionService service = service(repository, new ConfirmationTokenService(),
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC));

        IssuedA2uiAction issued = service.issue(hold, "surface-1");

        assertThat(issued.actionId()).isEqualTo("00000000-0000-0000-0000-000000000021");
        assertThat(issued.confirmationToken()).matches("[A-Za-z0-9_-]{43}");
        verify(repository).save(any(A2uiActionEntity.class));
    }

    @Test
    void rejectsWrongUserSurfaceAndTokenBeforeAnActionCanBeConsumed() {
        A2uiActionRepository repository = mock(A2uiActionRepository.class);
        ConfirmationTokenService tokens = new ConfirmationTokenService();
        String token = tokens.generate();
        A2uiActionEntity action = A2uiActionEntity.pending(88L, 7L, "surface-1", "CONFIRM_APPOINTMENT", tokens.sha256(token),
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        UUID actionId = UUID.randomUUID();
        ReflectionTestUtils.setField(action, "publicId", actionId);
        when(repository.findByPublicId(actionId)).thenReturn(Optional.of(action));
        A2uiActionService service = service(repository, tokens,
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.validate(8L, actionId.toString(), "surface-1", token)).getMessage()).contains("owner");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.validate(7L, actionId.toString(), "surface-other", token)).getMessage()).contains("surface");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.validate(7L, actionId.toString(), "surface-1", "forged")).getMessage()).contains("token");
        assertThat(action.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void confirmsOnlyTheHeldQuotaAndConsumesTheAction() {
        A2uiActionRepository actions = mock(A2uiActionRepository.class);
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        AppointmentCommandService appointments = mock(AppointmentCommandService.class);
        com.medora.appointment.repository.AppointmentRepository appointmentRepository = mock(com.medora.appointment.repository.AppointmentRepository.class);
        WaitlistEntryRepository waitlistEntries = mock(WaitlistEntryRepository.class);
        ConfirmationTokenService tokens = new ConfirmationTokenService();
        String token = tokens.generate();
        UUID actionId = UUID.randomUUID();
        A2uiActionEntity action = A2uiActionEntity.pending(88L, 7L, "surface-1", "CONFIRM_APPOINTMENT", tokens.sha256(token),
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(action, "publicId", actionId);
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1",
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "id", 88L);
        com.medora.masterdata.domain.ScheduleSlotEntity slot = mock(com.medora.masterdata.domain.ScheduleSlotEntity.class);
        com.medora.appointment.domain.AppointmentEntity appointment = mock(com.medora.appointment.domain.AppointmentEntity.class);
        UUID appointmentId = UUID.randomUUID();
        when(actions.findByPublicIdForUpdate(actionId)).thenReturn(Optional.of(action));
        when(holds.findByIdForUpdate(88L)).thenReturn(Optional.of(hold));
        when(slots.findByIdForUpdate(19L)).thenReturn(Optional.of(slot));
        when(appointments.confirmHeld(7L, hold, slot)).thenReturn(appointment);
        when(appointment.getId()).thenReturn(101L);
        when(appointment.getPublicId()).thenReturn(appointmentId);
        when(appointmentRepository.findById(101L)).thenReturn(Optional.of(appointment));
        WaitlistEntryEntity entry = WaitlistEntryEntity.waiting(7L, 19L, 0,
                Instant.parse("2026-09-21T13:59:00Z").atOffset(ZoneOffset.UTC));
        entry.offer(88L);
        when(waitlistEntries.findByOfferedHoldId(88L)).thenReturn(Optional.of(entry));
        A2uiActionService service = new A2uiActionService(actions, tokens,
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC), holds, slots, appointments, waitlistEntries, appointmentRepository);

        ConfirmedA2uiAction result = service.confirm(7L, actionId.toString(), "surface-1", token, "idem-1");
        ConfirmedA2uiAction repeated = service.confirm(7L, actionId.toString(), "surface-1", token, "idem-1");

        assertThat(result.appointmentId()).isEqualTo(appointmentId.toString());
        assertThat(action.getStatus()).isEqualTo("CONSUMED");
        assertThat(hold.getStatus()).isEqualTo("CONFIRMED");
        assertThat(hold.getConfirmedAppointmentId()).isEqualTo(101L);
        assertThat(entry.getStatus()).isEqualTo("CONFIRMED");
        assertThat(repeated.appointmentId()).isEqualTo(appointmentId.toString());
        verify(appointments, times(1)).confirmHeld(7L, hold, slot);
    }

    private A2uiActionService service(A2uiActionRepository repository, ConfirmationTokenService tokens, Clock clock) {
        return new A2uiActionService(repository, tokens, clock, mock(AppointmentHoldRepository.class),
                mock(ScheduleSlotRepository.class), mock(AppointmentCommandService.class), mock(WaitlistEntryRepository.class));
    }
}

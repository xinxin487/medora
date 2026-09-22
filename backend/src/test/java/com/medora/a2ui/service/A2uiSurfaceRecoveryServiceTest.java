package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class A2uiSurfaceRecoveryServiceTest {

    @Test
    void replacesThePendingActionWithANewOneTimeTokenForTheSameConversationSurface() {
        A2uiActionRepository actions = mock(A2uiActionRepository.class);
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        ConfirmationTokenService tokens = new ConfirmationTokenService();
        UUID conversationId = UUID.fromString("00000000-0000-0000-0000-000000000077");
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1",
                Instant.parse("2026-09-22T10:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "id", 88L);
        ReflectionTestUtils.setField(hold, "publicId", UUID.randomUUID());
        A2uiActionEntity existing = A2uiActionEntity.pending(88L, 7L, conversationId,
                "appointment-surface", "CONFIRM_APPOINTMENT", tokens.sha256("previous-token"),
                Instant.parse("2026-09-22T10:02:00Z").atOffset(ZoneOffset.UTC));
        when(actions.findPendingByUserIdAndConversationIdForUpdate(7L, conversationId))
                .thenReturn(List.of(existing));
        when(holds.findById(88L)).thenReturn(Optional.of(hold));
        when(actions.save(any(A2uiActionEntity.class))).thenAnswer(invocation -> {
            A2uiActionEntity replacement = invocation.getArgument(0);
            ReflectionTestUtils.setField(replacement, "publicId", UUID.randomUUID());
            return replacement;
        });
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(slot, "fee", new BigDecimal("25.00"));
        when(slots.findById(19L)).thenReturn(Optional.of(slot));

        A2uiActionService actionService = new A2uiActionService(actions, tokens, clock, holds, slots,
                mock(com.medora.appointment.service.AppointmentCommandService.class));
        A2uiSurfaceRecoveryService service = new A2uiSurfaceRecoveryService(actions, holds, actionService,
                new AppointmentSurfaceFactory(slots, new com.medora.a2ui.protocol.A2uiCatalogValidator()), clock);

        var surfaces = service.restore(7L, conversationId);

        assertThat(existing.getStatus()).isEqualTo("CANCELLED");
        assertThat(surfaces).hasSize(1);
        assertThat(surfaces.getFirst().surfaceId()).isEqualTo("appointment-surface");
        assertThat(surfaces.getFirst().dataModel().get("confirmationToken")).isNotEqualTo("previous-token");
    }
}

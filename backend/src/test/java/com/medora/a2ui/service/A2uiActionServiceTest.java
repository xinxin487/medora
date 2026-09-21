package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.appointment.hold.AppointmentHoldEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        A2uiActionService service = new A2uiActionService(repository, new ConfirmationTokenService(),
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC));

        IssuedA2uiAction issued = service.issue(hold, "surface-1");

        assertThat(issued.actionId()).isEqualTo("00000000-0000-0000-0000-000000000021");
        assertThat(issued.confirmationToken()).matches("[A-Za-z0-9_-]{43}");
        verify(repository).save(any(A2uiActionEntity.class));
    }
}

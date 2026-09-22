package com.medora.a2ui.service;

import com.medora.a2ui.protocol.A2uiCatalogValidator;
import com.medora.a2ui.protocol.A2uiMessage;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentSurfaceFactoryTest {
    @Test
    void createsAConfirmationSurfaceFromServerSideHoldAndActionOnly() {
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "id", 19L);
        ReflectionTestUtils.setField(slot, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(slot, "fee", new BigDecimal("25.00"));
        when(slots.findById(19L)).thenReturn(Optional.of(slot));
        AppointmentHoldEntity hold = AppointmentHoldEntity.pending(7L, 19L, "idem-1",
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        ReflectionTestUtils.setField(hold, "publicId", UUID.randomUUID());
        IssuedA2uiAction action = new IssuedA2uiAction(UUID.randomUUID().toString(), "surface-1", "raw-token",
                Instant.parse("2026-09-21T14:02:00Z").atOffset(ZoneOffset.UTC));
        AppointmentSurfaceFactory factory = new AppointmentSurfaceFactory(slots, new A2uiCatalogValidator());

        A2uiMessage surface = factory.create(hold, action);

        assertThat(surface.event()).isEqualTo("createSurface");
        assertThat(surface.dataModel()).containsEntry("actionId", action.actionId()).containsEntry("confirmationToken", "raw-token")
                .doesNotContainKey("tokenHash");
        assertThat(surface.components()).anySatisfy(component -> assertThat(component.get("type")).isEqualTo("Button"));
    }
}

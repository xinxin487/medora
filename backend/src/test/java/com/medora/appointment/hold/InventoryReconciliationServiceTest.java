package com.medora.appointment.hold;

import com.medora.appointment.repository.AppointmentRepository;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryReconciliationServiceTest {

    @Test
    void reportsCounterDriftWithoutChangingConfirmedBusinessData() {
        ScheduleSlotRepository slots = mock(ScheduleSlotRepository.class);
        AppointmentHoldRepository holds = mock(AppointmentHoldRepository.class);
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        UUID publicId = UUID.randomUUID();
        ReflectionTestUtils.setField(slot, "id", 19L);
        ReflectionTestUtils.setField(slot, "publicId", publicId);
        ReflectionTestUtils.setField(slot, "totalQuota", 10);
        ReflectionTestUtils.setField(slot, "availableQuota", 8);
        ReflectionTestUtils.setField(slot, "reservedQuota", 0);
        when(slots.findAll()).thenReturn(List.of(slot));
        when(holds.countBySlotIdAndStatus(19L, "PENDING_CONFIRMATION")).thenReturn(1L);
        when(appointments.countBySlot_IdAndStatus(19L, "CONFIRMED")).thenReturn(2L);
        InventoryReconciliationService service = new InventoryReconciliationService(slots, holds, appointments);

        List<InventoryDiscrepancy> discrepancies = service.reconcile();

        assertThat(discrepancies).singleElement().satisfies(drift -> {
            assertThat(drift.slotId()).isEqualTo(publicId.toString());
            assertThat(drift.expectedAvailable()).isEqualTo(7);
            assertThat(drift.expectedReserved()).isEqualTo(1);
        });
        assertThat(slot.getAvailableQuota()).isEqualTo(8);
        assertThat(slot.getReservedQuota()).isZero();
    }
}

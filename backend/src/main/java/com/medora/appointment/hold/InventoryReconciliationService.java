package com.medora.appointment.hold;

import com.medora.appointment.repository.AppointmentRepository;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryReconciliationService {
    private final ScheduleSlotRepository slots;
    private final AppointmentHoldRepository holds;
    private final AppointmentRepository appointments;

    public InventoryReconciliationService(ScheduleSlotRepository slots, AppointmentHoldRepository holds,
                                          AppointmentRepository appointments) {
        this.slots = slots; this.holds = holds; this.appointments = appointments;
    }

    public List<InventoryDiscrepancy> reconcile() {
        return slots.findAll().stream().map(this::findDrift).filter(java.util.Objects::nonNull).toList();
    }

    private InventoryDiscrepancy findDrift(ScheduleSlotEntity slot) {
        int pendingHolds = Math.toIntExact(holds.countBySlotIdAndStatus(slot.getId(), "PENDING_CONFIRMATION"));
        int confirmed = Math.toIntExact(appointments.countBySlot_IdAndStatus(slot.getId(), "CONFIRMED"));
        int expectedReserved = pendingHolds;
        int expectedAvailable = slot.getTotalQuota() - confirmed - pendingHolds;
        if (expectedAvailable == slot.getAvailableQuota() && expectedReserved == slot.getReservedQuota()) return null;
        return new InventoryDiscrepancy(slot.getPublicId().toString(), expectedAvailable, slot.getAvailableQuota(),
                expectedReserved, slot.getReservedQuota());
    }
}

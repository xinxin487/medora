package com.medora.appointment.waitlist;

import com.medora.appointment.hold.SlotReleased;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.hold.AppointmentHoldService;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Component
public class WaitlistPromotionHandler {
    private final WaitlistEntryRepository entries; private final WaitlistIndex index; private final ScheduleSlotRepository slots;
    private final AppointmentHoldService holds; private final AppointmentHoldRepository holdRepository; private final Clock clock;
    public WaitlistPromotionHandler(WaitlistEntryRepository entries, WaitlistIndex index, ScheduleSlotRepository slots,
                                    AppointmentHoldService holds, AppointmentHoldRepository holdRepository, Clock clock) {
        this.entries=entries; this.index=index; this.slots=slots; this.holds=holds; this.holdRepository=holdRepository; this.clock=clock;
    }
    @EventListener @Transactional
    public void onSlotReleased(SlotReleased event) {
        for (String member : index.popNext(event.slotId())) {
            final long id; try { id=Long.parseLong(member); } catch (NumberFormatException ex) { index.remove(event.slotId(), member); continue; }
            var entry=entries.findByIdForUpdate(id); if (entry.isEmpty() || !"WAITING".equals(entry.get().getStatus())) { index.remove(event.slotId(), member); continue; }
            var slot = slots.findById(event.slotId()).orElseThrow(() -> new IllegalStateException("waitlist slot not found"));
            String idempotencyKey = "WAIT-" + id;
            holds.prepare(entry.get().getUserId(), slot.getPublicId().toString(), idempotencyKey, "WAITLIST");
            var hold = holdRepository.findByUserIdAndIdempotencyKey(entry.get().getUserId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("waitlist hold is missing"));
            entry.get().offer(hold.getId()); return;
        }
    }
}

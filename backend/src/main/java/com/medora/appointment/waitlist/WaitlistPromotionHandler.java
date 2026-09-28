package com.medora.appointment.waitlist;

import com.medora.appointment.hold.SlotReleased;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.hold.AppointmentHoldService;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import com.medora.a2ui.service.A2uiActionService;
import com.medora.a2ui.service.AppointmentSurfaceFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.time.Clock;

@Component
public class WaitlistPromotionHandler {
    private final WaitlistEntryRepository entries; private final WaitlistIndex index; private final ScheduleSlotRepository slots;
    private final AppointmentHoldService holds; private final AppointmentHoldRepository holdRepository; private final Clock clock;
    private final A2uiActionService actions; private final AppointmentSurfaceFactory surfaces;
    public WaitlistPromotionHandler(WaitlistEntryRepository entries, WaitlistIndex index, ScheduleSlotRepository slots,
                                    AppointmentHoldService holds, AppointmentHoldRepository holdRepository,
                                    A2uiActionService actions, AppointmentSurfaceFactory surfaces, Clock clock) {
        this.entries=entries; this.index=index; this.slots=slots; this.holds=holds; this.holdRepository=holdRepository;
        this.actions=actions; this.surfaces=surfaces; this.clock=clock;
    }
    @EventListener @Transactional
    public void onSlotReleased(SlotReleased event) {
        while (true) {
            var claimed = index.popNext(event.slotId());
            if (claimed.isEmpty()) return;
            String member = claimed.get();
            final long id; try { id=Long.parseLong(member); } catch (NumberFormatException ex) { index.remove(event.slotId(), member); continue; }
            var entry=entries.findByIdForUpdate(id); if (entry.isEmpty() || !"WAITING".equals(entry.get().getStatus())) { index.remove(event.slotId(), member); continue; }
            try {
                var slot = slots.findById(event.slotId()).orElseThrow(() -> new IllegalStateException("waitlist slot not found"));
                String idempotencyKey = "WAIT-" + id;
                holds.prepare(entry.get().getUserId(), slot.getPublicId().toString(), idempotencyKey, "WAITLIST");
                var hold = holdRepository.findByUserIdAndIdempotencyKey(entry.get().getUserId(), idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("waitlist hold is missing"));
                var action = actions.issue(hold, "waitlist-" + id);
                surfaces.create(hold, action);
                entry.get().offer(hold.getId()); return;
            } catch (RuntimeException ex) {
                requeueAfterRollback(entry.get());
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                }
                return;
            }
        }
    }

    private void requeueAfterRollback(WaitlistEntryEntity entry) {
        Runnable requeue = () -> {
            try { index.add(entry.getSlotId(), String.valueOf(entry.getId()), score(entry)); }
            catch (RuntimeException ignored) { }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { requeue.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) requeue.run();
            }
        });
    }

    private double score(WaitlistEntryEntity entry) {
        return (100 - entry.getPriority()) * 1_000_000_000_000d
                + entry.getJoinedAt().toInstant().toEpochMilli();
    }
}

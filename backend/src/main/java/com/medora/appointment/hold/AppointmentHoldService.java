package com.medora.appointment.hold;

import com.medora.common.exception.BusinessException;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppointmentHoldService {
    private static final java.time.Duration HOLD_DURATION = java.time.Duration.ofMinutes(2);
    private final AppointmentHoldRepository holds;
    private final ScheduleSlotRepository slots;
    private final SlotLock slotLock;
    private final Clock clock;
    private final HoldExpiryIndex expiryIndex;

    public AppointmentHoldService(AppointmentHoldRepository holds, ScheduleSlotRepository slots, SlotLock slotLock, Clock clock) {
        this(holds, slots, slotLock, clock, new HoldExpiryIndex() {
            @Override public void schedule(String holdId, OffsetDateTime expiresAt) { }
            @Override public void remove(String holdId) { }
            @Override public java.util.Set<String> dueBefore(OffsetDateTime now, int limit) { return java.util.Set.of(); }
        });
    }

    @Autowired
    public AppointmentHoldService(AppointmentHoldRepository holds, ScheduleSlotRepository slots, SlotLock slotLock, Clock clock,
                                  HoldExpiryIndex expiryIndex) {
        this.holds = holds; this.slots = slots; this.slotLock = slotLock; this.clock = clock; this.expiryIndex = expiryIndex;
    }

    @Transactional
    public PreparedAppointment prepare(Long userId, String rawSlotId, String idempotencyKey, String source) {
        Optional<AppointmentHoldEntity> existing = holds.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) return toPrepared(existing.get(), true);
        UUID slotId = parseSlotId(rawSlotId);
        SlotLock.Lease lease = null;
        try { lease = slotLock.acquire(slotId); } catch (RuntimeException ignored) { }
        try {
            ScheduleSlotEntity slot = slots.findByPublicIdForUpdate(slotId).orElseThrow(() ->
                    new BusinessException("MASTER_SLOT_NOT_FOUND", HttpStatus.NOT_FOUND, "schedule slot not found"));
            try { slot.holdOne(); } catch (IllegalStateException ex) {
                throw new BusinessException("APPOINTMENT_SLOT_FULL", HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
            }
            AppointmentHoldEntity hold = AppointmentHoldEntity.pending(userId, slot.getId(), idempotencyKey,
                    OffsetDateTime.now(clock).plus(HOLD_DURATION));
            AppointmentHoldEntity saved = holds.save(hold);
            try { expiryIndex.schedule(saved.getPublicId().toString(), saved.getExpiresAt()); } catch (RuntimeException ignored) { }
            return toPrepared(saved, false);
        } finally {
            if (lease != null) lease.close();
        }
    }

    private PreparedAppointment toPrepared(AppointmentHoldEntity hold, boolean reused) {
        return new PreparedAppointment(hold.getPublicId().toString(), String.valueOf(hold.getSlotId()), hold.getExpiresAt(), reused);
    }
    private UUID parseSlotId(String rawSlotId) {
        try { return UUID.fromString(rawSlotId); }
        catch (RuntimeException ex) { throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST, "slotId must be a valid uuid"); }
    }
}

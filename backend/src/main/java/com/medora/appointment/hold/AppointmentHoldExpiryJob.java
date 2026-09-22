package com.medora.appointment.hold;

import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import com.medora.appointment.waitlist.WaitlistEntryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class AppointmentHoldExpiryJob {
    private final AppointmentHoldRepository holds;
    private final ScheduleSlotRepository slots;
    private final HoldExpiryIndex expiryIndex;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final WaitlistEntryRepository waitlistEntries;

    public AppointmentHoldExpiryJob(AppointmentHoldRepository holds, ScheduleSlotRepository slots, HoldExpiryIndex expiryIndex,
                                    ApplicationEventPublisher events, Clock clock, WaitlistEntryRepository waitlistEntries) {
        this.holds = holds; this.slots = slots; this.expiryIndex = expiryIndex; this.events = events; this.clock = clock; this.waitlistEntries = waitlistEntries;
    }

    @Scheduled(fixedDelayString = "${medora.appointment.hold-expiry-delay-ms:30000}")
    @Transactional
    public void expireOverdue() {
        for (AppointmentHoldEntity hold : holds.findPendingExpiredForUpdate(OffsetDateTime.now(clock))) {
            if (!"PENDING_CONFIRMATION".equals(hold.getStatus())) continue;
            ScheduleSlotEntity slot = slots.findByIdForUpdate(hold.getSlotId())
                    .orElseThrow(() -> new IllegalStateException("schedule slot not found for expired hold"));
            hold.expire();
            waitlistEntries.findByOfferedHoldId(hold.getId()).ifPresent(entry -> entry.expireOffer());
            slot.releaseHeldOne();
            expiryIndex.remove(hold.getPublicId().toString());
            publishAfterCommit(new SlotReleased(slot.getId()));
        }
    }

    private void publishAfterCommit(SlotReleased event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { events.publishEvent(event); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { events.publishEvent(event); }
        });
    }
}

package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.appointment.domain.AppointmentEntity;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.service.AppointmentCommandService;
import com.medora.appointment.repository.AppointmentRepository;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import com.medora.appointment.waitlist.WaitlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class A2uiActionService {
    private final A2uiActionRepository repository;
    private final ConfirmationTokenService tokens;
    private final Clock clock;
    private final AppointmentHoldRepository holdRepository;
    private final ScheduleSlotRepository slotRepository;
    private final AppointmentCommandService appointmentCommandService;
    private final WaitlistEntryRepository waitlistEntries;
    private final AppointmentRepository appointments;

    @Autowired
    public A2uiActionService(A2uiActionRepository repository, ConfirmationTokenService tokens, Clock clock,
                             AppointmentHoldRepository holdRepository, ScheduleSlotRepository slotRepository,
                             AppointmentCommandService appointmentCommandService, WaitlistEntryRepository waitlistEntries,
                             AppointmentRepository appointments) {
        this.repository = repository; this.tokens = tokens; this.clock = clock;
        this.holdRepository = holdRepository; this.slotRepository = slotRepository;
        this.appointmentCommandService = appointmentCommandService;
        this.waitlistEntries = waitlistEntries;
        this.appointments = appointments;
    }

    @Deprecated
    public A2uiActionService(A2uiActionRepository repository, ConfirmationTokenService tokens, Clock clock,
                             AppointmentHoldRepository holdRepository, ScheduleSlotRepository slotRepository,
                             AppointmentCommandService appointmentCommandService, WaitlistEntryRepository waitlistEntries) {
        this(repository, tokens, clock, holdRepository, slotRepository, appointmentCommandService, waitlistEntries, null);
    }

    @Deprecated
    public A2uiActionService(A2uiActionRepository repository, ConfirmationTokenService tokens, Clock clock,
                             AppointmentHoldRepository holdRepository, ScheduleSlotRepository slotRepository,
                             AppointmentCommandService appointmentCommandService) {
        this(repository, tokens, clock, holdRepository, slotRepository, appointmentCommandService, null, null);
    }

    @Transactional
    public IssuedA2uiAction issue(AppointmentHoldEntity hold, String surfaceId) {
        return issue(hold, surfaceId, null);
    }

    @Transactional
    public IssuedA2uiAction issue(AppointmentHoldEntity hold, String surfaceId, UUID conversationId) {
        if (!"PENDING_CONFIRMATION".equals(hold.getStatus())) throw new IllegalStateException("appointment hold is not pending");
        String rawToken = tokens.generate();
        OffsetDateTime expiry = hold.getExpiresAt().isBefore(OffsetDateTime.now(clock)) ? OffsetDateTime.now(clock) : hold.getExpiresAt();
        A2uiActionEntity saved = repository.save(A2uiActionEntity.pending(hold.getId(), hold.getUserId(), conversationId, surfaceId,
                "CONFIRM_APPOINTMENT", tokens.sha256(rawToken), expiry));
        return new IssuedA2uiAction(saved.getPublicId().toString(), surfaceId, rawToken, expiry);
    }

    @Transactional
    public IssuedA2uiAction replaceForRecovery(AppointmentHoldEntity hold, A2uiActionEntity existing) {
        if (!"PENDING".equals(existing.getStatus())) throw new IllegalStateException("a2ui action is no longer pending");
        existing.cancel();
        return issue(hold, existing.getSurfaceId(), existing.getConversationId());
    }

    /**
     * Checks that a confirmation request is still bound to the authenticated user and
     * the A2UI surface that created it.  This method deliberately has no state change:
     * callers must complete all validation before touching a hold or slot inventory.
     */
    @Transactional(readOnly = true)
    public A2uiActionEntity validate(Long userId, String rawActionId, String surfaceId, String rawToken) {
        final UUID actionId;
        try {
            actionId = UUID.fromString(rawActionId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("a2ui action not found");
        }
        A2uiActionEntity action = repository.findByPublicId(actionId)
                .orElseThrow(() -> new IllegalArgumentException("a2ui action not found"));
        validateBinding(action, userId, surfaceId, rawToken);
        if (!"PENDING".equals(action.getStatus())) throw new IllegalStateException("a2ui action is no longer pending");
        return action;
    }

    @Transactional
    public ConfirmedA2uiAction confirm(Long userId, String rawActionId, String surfaceId, String rawToken,
                                       String idempotencyKey) {
        final UUID actionId;
        try { actionId = UUID.fromString(rawActionId); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("a2ui action not found"); }
        A2uiActionEntity action = repository.findByPublicIdForUpdate(actionId)
                .orElseThrow(() -> new IllegalArgumentException("a2ui action not found"));
        validateBinding(action, userId, surfaceId, rawToken);
        AppointmentHoldEntity hold = holdRepository.findByIdForUpdate(action.getHoldId())
                .orElseThrow(() -> new IllegalStateException("appointment hold not found"));
        if (!hold.getUserId().equals(userId)) throw new IllegalArgumentException("appointment hold owner mismatch");
        if (idempotencyKey == null || !idempotencyKey.equals(hold.getIdempotencyKey())) {
            throw new IllegalArgumentException("appointment confirmation idempotency key mismatch");
        }
        if ("CONSUMED".equals(action.getStatus())) {
            if (hold.getConfirmedAppointmentId() == null) throw new IllegalStateException("confirmed appointment is missing");
            if (appointments == null) return new ConfirmedA2uiAction(action.getPublicId().toString(), action.getStatus(), hold.getConfirmedAppointmentId().toString());
            String appointmentId = appointments.findById(hold.getConfirmedAppointmentId())
                    .orElseThrow(() -> new IllegalStateException("confirmed appointment is missing"))
                    .getPublicId().toString();
            return new ConfirmedA2uiAction(action.getPublicId().toString(), action.getStatus(), appointmentId);
        }
        if (!"PENDING".equals(action.getStatus())) throw new IllegalStateException("a2ui action is no longer pending");
        if (!"PENDING_CONFIRMATION".equals(hold.getStatus()) || !hold.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new IllegalStateException("appointment hold is no longer pending");
        }
        ScheduleSlotEntity slot = slotRepository.findByIdForUpdate(hold.getSlotId())
                .orElseThrow(() -> new IllegalStateException("schedule slot not found"));
        AppointmentEntity appointment = appointmentCommandService.confirmHeld(userId, hold, slot);
        hold.confirm(appointment.getId());
        action.consume(OffsetDateTime.now(clock));
        if (waitlistEntries != null) waitlistEntries.findByOfferedHoldId(hold.getId()).ifPresent(entry -> entry.confirm());
        return new ConfirmedA2uiAction(action.getPublicId().toString(), action.getStatus(), appointment.getPublicId().toString());
    }

    private void validateBinding(A2uiActionEntity action, Long userId, String surfaceId, String rawToken) {
        if (!action.getUserId().equals(userId)) throw new IllegalArgumentException("a2ui action owner mismatch");
        if (!action.getSurfaceId().equals(surfaceId)) throw new IllegalArgumentException("a2ui action surface mismatch");
        if (!action.getExpiresAt().isAfter(OffsetDateTime.now(clock))) throw new IllegalStateException("a2ui action has expired");
        byte[] expected = action.getTokenHash().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = tokens.sha256(rawToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) throw new IllegalArgumentException("a2ui action token is invalid");
    }
}

package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.appointment.hold.AppointmentHoldEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class A2uiActionService {
    private final A2uiActionRepository repository;
    private final ConfirmationTokenService tokens;
    private final Clock clock;

    public A2uiActionService(A2uiActionRepository repository, ConfirmationTokenService tokens, Clock clock) {
        this.repository = repository; this.tokens = tokens; this.clock = clock;
    }

    @Transactional
    public IssuedA2uiAction issue(AppointmentHoldEntity hold, String surfaceId) {
        if (!"PENDING_CONFIRMATION".equals(hold.getStatus())) throw new IllegalStateException("appointment hold is not pending");
        String rawToken = tokens.generate();
        OffsetDateTime expiry = hold.getExpiresAt().isBefore(OffsetDateTime.now(clock)) ? OffsetDateTime.now(clock) : hold.getExpiresAt();
        A2uiActionEntity saved = repository.save(A2uiActionEntity.pending(hold.getId(), hold.getUserId(), surfaceId,
                "CONFIRM_APPOINTMENT", tokens.sha256(rawToken), expiry));
        return new IssuedA2uiAction(saved.getPublicId().toString(), surfaceId, rawToken, expiry);
    }
}

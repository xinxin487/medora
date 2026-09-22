package com.medora.a2ui.service;

import com.medora.a2ui.domain.A2uiActionEntity;
import com.medora.a2ui.domain.A2uiActionRepository;
import com.medora.a2ui.protocol.A2uiMessage;
import com.medora.appointment.hold.AppointmentHoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reissues short-lived client tokens instead of persisting a recoverable raw token. */
@Service
public class A2uiSurfaceRecoveryService {
    private final A2uiActionRepository actions;
    private final AppointmentHoldRepository holds;
    private final A2uiActionService actionService;
    private final AppointmentSurfaceFactory surfaces;
    private final Clock clock;

    public A2uiSurfaceRecoveryService(A2uiActionRepository actions, AppointmentHoldRepository holds,
                                      A2uiActionService actionService, AppointmentSurfaceFactory surfaces, Clock clock) {
        this.actions = actions;
        this.holds = holds;
        this.actionService = actionService;
        this.surfaces = surfaces;
        this.clock = clock;
    }

    @Transactional
    public List<A2uiMessage> restore(Long userId, UUID conversationId) {
        List<A2uiMessage> restored = new ArrayList<>();
        for (A2uiActionEntity existing : actions.findPendingByUserIdAndConversationIdForUpdate(userId, conversationId)) {
            var hold = holds.findById(existing.getHoldId()).orElse(null);
            if (hold == null || !"PENDING_CONFIRMATION".equals(hold.getStatus())) {
                existing.cancel();
                continue;
            }
            if (!existing.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
                existing.expire();
                continue;
            }
            var replacement = actionService.replaceForRecovery(hold, existing);
            restored.add(surfaces.create(hold, replacement));
        }
        return restored;
    }
}

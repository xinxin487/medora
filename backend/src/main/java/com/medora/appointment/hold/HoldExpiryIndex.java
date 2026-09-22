package com.medora.appointment.hold;

import java.time.OffsetDateTime;
import java.util.Set;

/** Redis is an optional fast path; the appointment_hold table remains authoritative. */
public interface HoldExpiryIndex {
    void schedule(String holdId, OffsetDateTime expiresAt);
    void remove(String holdId);
    Set<String> dueBefore(OffsetDateTime now, int limit);
}

package com.medora.appointment.hold;

import java.util.UUID;

@FunctionalInterface
public interface SlotLock {
    Lease acquire(UUID slotId);

    @FunctionalInterface
    interface Lease extends AutoCloseable {
        @Override void close();
    }
}

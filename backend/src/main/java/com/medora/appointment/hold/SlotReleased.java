package com.medora.appointment.hold;

/** Published after the transaction that returns held quota has committed. */
public record SlotReleased(Long slotId) {
}

package com.medora.appointment.hold;

/** Read-only diagnostic; reconciliation never rewrites clinical booking records. */
public record InventoryDiscrepancy(String slotId, int expectedAvailable, int actualAvailable,
                                   int expectedReserved, int actualReserved) {
}

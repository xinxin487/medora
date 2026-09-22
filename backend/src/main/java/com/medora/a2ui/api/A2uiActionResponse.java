package com.medora.a2ui.api;

/** Token hashes and hold snapshots intentionally never leave the server. */
public record A2uiActionResponse(String actionId, String status, String appointmentId) {
}

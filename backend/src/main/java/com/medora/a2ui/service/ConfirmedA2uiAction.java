package com.medora.a2ui.service;

/** Server result of consuming a one-time A2UI action. */
public record ConfirmedA2uiAction(String actionId, String status, String appointmentId, java.util.UUID conversationId) {
}

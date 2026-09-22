package com.medora.a2ui.service;

import com.medora.a2ui.protocol.A2uiCatalogValidator;
import com.medora.a2ui.protocol.A2uiMessage;
import com.medora.appointment.hold.AppointmentHoldEntity;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Builds booking UI solely from authoritative hold, action, and slot records. */
@Service
public class AppointmentSurfaceFactory {
    private final ScheduleSlotRepository slots;
    private final A2uiCatalogValidator validator;

    public AppointmentSurfaceFactory(ScheduleSlotRepository slots, A2uiCatalogValidator validator) {
        this.slots = slots; this.validator = validator;
    }

    public A2uiMessage create(AppointmentHoldEntity hold, IssuedA2uiAction action) {
        ScheduleSlotEntity slot = slots.findById(hold.getSlotId())
                .orElseThrow(() -> new IllegalStateException("schedule slot not found"));
        String rootId = "appointment-confirmation";
        List<Map<String, Object>> components = List.of(
                component(rootId, "Card", Map.of()),
                component("booking-column", "Column", Map.of()),
                component("booking-title", "Text", Map.of("text", "确认预约")),
                component("booking-status", "Badge", Map.of("text", "待确认")),
                component("booking-divider", "Divider", Map.of()),
                component("booking-actions", "Row", Map.of()),
                component("booking-confirm", "Button", Map.of("text", "确认预约", "actionId", action.actionId()))
        );
        validator.validate(components, rootId, action.actionId());
        Map<String, Object> data = Map.of(
                "holdId", hold.getPublicId().toString(),
                "slotId", slot.getPublicId().toString(),
                "fee", slot.getFee() == null ? "" : slot.getFee().toPlainString(),
                "actionId", action.actionId(),
                "surfaceId", action.surfaceId(),
                "confirmationToken", action.confirmationToken(),
                "idempotencyKey", hold.getIdempotencyKey(),
                "expiresAt", action.expiresAt().toString()
        );
        return new A2uiMessage("createSurface", action.surfaceId(), rootId, components, data);
    }

    private Map<String, Object> component(String id, String type, Map<String, Object> props) {
        return Map.of("id", id, "type", type, "props", props);
    }
}

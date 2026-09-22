package com.medora.a2ui.protocol;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Enforces the deliberately small, native-only A2UI component catalog. */
@Component
public class A2uiCatalogValidator {
    private static final Set<String> ALLOWED_TYPES = Set.of("Text", "Card", "Column", "Row", "Button", "Badge", "Divider");

    public void validate(List<Map<String, Object>> components, String rootId, String storedActionId) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> component : components) {
            String id = string(component.get("id"));
            String type = string(component.get("type"));
            if (id == null || !ids.add(id)) throw new IllegalArgumentException("duplicate or missing component id");
            if (!ALLOWED_TYPES.contains(type)) throw new IllegalArgumentException("unsupported A2UI component type");
            Object rawProps = component.get("props");
            if (rawProps instanceof Map<?, ?> props) validateProps(props, type, storedActionId);
        }
        if (!ids.contains(rootId)) throw new IllegalArgumentException("A2UI root component is missing");
    }

    private void validateProps(Map<?, ?> props, String type, String storedActionId) {
        for (Map.Entry<?, ?> entry : props.entrySet()) {
            String key = String.valueOf(entry.getKey()).toLowerCase();
            String value = String.valueOf(entry.getValue()).toLowerCase();
            if (key.contains("html") || key.contains("script") || value.contains("<script") || value.contains("<html")) {
                throw new IllegalArgumentException("A2UI inline HTML or scripts are not allowed");
            }
            if (key.contains("url") || value.startsWith("http://") || value.startsWith("https://")) {
                throw new IllegalArgumentException("A2UI external URLs are not allowed");
            }
        }
        if ("Button".equals(type) && !storedActionId.equals(string(props.get("actionId")))) {
            throw new IllegalArgumentException("A2UI button action is not server-issued");
        }
    }

    private String string(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
}

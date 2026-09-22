package com.medora.a2ui.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2uiCatalogValidatorTest {
    private final A2uiCatalogValidator validator = new A2uiCatalogValidator();

    @Test
    void acceptsOnlyTheSevenServerAuthoredComponentTypes() {
        assertThatCode(() -> validator.validate(List.of(
                component("root", "Card", Map.of()), component("body", "Column", Map.of()),
                component("title", "Text", Map.of("text", "确认预约")), component("tag", "Badge", Map.of("text", "待确认")),
                component("line", "Divider", Map.of()), component("row", "Row", Map.of()),
                component("confirm", "Button", Map.of("actionId", "action-1"))
        ), "root", "action-1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsUntrustedComponentShapesAndActions() {
        assertThatThrownBy(() -> validator.validate(List.of(component("root", "Html", Map.of("html", "<script>alert(1)</script>"))), "root", "action-1"))
                .hasMessageContaining("component type");
        assertThatThrownBy(() -> validator.validate(List.of(component("root", "Text", Map.of()), component("root", "Text", Map.of())), "root", "action-1"))
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> validator.validate(List.of(component("root", "Text", Map.of())), "missing", "action-1"))
                .hasMessageContaining("root");
        assertThatThrownBy(() -> validator.validate(List.of(component("root", "Text", Map.of("url", "https://bad.example"))), "root", "action-1"))
                .hasMessageContaining("URLs");
        assertThatThrownBy(() -> validator.validate(List.of(component("root", "Button", Map.of("actionId", "forged"))), "root", "action-1"))
                .hasMessageContaining("action");
    }

    private Map<String, Object> component(String id, String type, Map<String, Object> props) {
        return Map.of("id", id, "type", type, "props", props);
    }
}

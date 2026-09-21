package com.medora.toolgateway.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolAuditSanitizerTest {

    @Test
    void redactsSensitiveArgumentsWhileKeepingOperationalIdentifiersAndHash() {
        ToolAuditSanitizer sanitizer = new ToolAuditSanitizer();

        ToolAuditSanitizer.SanitizedArguments result = sanitizer.sanitize(Map.of(
                "appointmentId", "a-001",
                "email", "patient@example.com",
                "password", "plain-password",
                "symptoms", "持续胸痛三天",
                "nested", Map.of("apiKey", "sk-secret", "slotId", "s-001")
        ));

        assertThat(result.summary()).containsEntry("appointmentId", "a-001")
                .containsEntry("email", "***")
                .containsEntry("password", "***")
                .containsEntry("symptoms", "***");
        assertThat((Map<String, Object>) result.summary().get("nested"))
                .containsEntry("apiKey", "***")
                .containsEntry("slotId", "s-001");
        assertThat(result.summary().toString()).doesNotContain("patient@example.com", "plain-password", "持续胸痛三天", "sk-secret");
        assertThat(result.argumentsHash()).matches("[a-f0-9]{64}");
    }
}

package com.medora.eval.context;

import com.medora.eval.report.EvidenceReportSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextEfficiencyReportTest {
    @Test
    @SuppressWarnings("unchecked")
    void reportsSchemaAndContextSelectionEvidence() {
        Map<String, Object> report = ContextEfficiencyReport.buildReport();
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");

        assertThat(report).containsKeys("generatedAt", "gitCommit", "fixtureVersion", "featureFlags",
                "command", "metrics", "environment");
        assertThat(metrics).containsKeys(
                "legacyEstimatedUnits", "m1EstimatedUnits", "selectedMemoryCount", "duplicateCount",
                "rejectionReasons", "legacyEarlyFactPresent", "m1EarlyFactPresent");
        assertThat(metrics).containsEntry("legacyEarlyFactPresent", false)
                .containsEntry("m1EarlyFactPresent", true);
    }

    @Test
    void rejectsSensitiveFieldNamesAtAnyDepth() {
        for (String forbidden : new String[]{"token", "password", "secret", "email", "message", "content"}) {
            assertThatThrownBy(() -> EvidenceReportSupport.validateSafe(Map.of("metrics", Map.of(forbidden, "x"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(forbidden);
        }
    }

    @Test
    void writesJsonAndMarkdown(@TempDir Path output) throws Exception {
        ContextEfficiencyReport.writeReport(output);

        assertThat(Files.readString(output.resolve("context-efficiency.json")))
                .contains("fixtureVersion", "legacyEstimatedUnits", "m1EarlyFactPresent");
        assertThat(Files.readString(output.resolve("context-efficiency.md")))
                .contains("Context efficiency", "Required early fact", "Rejection reasons");
    }
}

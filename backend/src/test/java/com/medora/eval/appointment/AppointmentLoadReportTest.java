package com.medora.eval.appointment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentLoadReportTest {
    @Test
    @SuppressWarnings("unchecked")
    void summarizesBalancedConcurrencyFixture() {
        AppointmentLoadReport.LoadResult input = new AppointmentLoadReport.LoadResult(
                100, 10, 10, 89, 1, 0, 0, 10, 0, List.of(2L, 4L, 6L, 8L, 10L), "DATABASE_FALLBACK");

        Map<String, Object> report = AppointmentLoadReport.buildReport(input);
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");

        assertThat(report).containsKeys("generatedAt", "gitCommit", "fixtureVersion", "featureFlags",
                "command", "metrics", "environment");
        assertThat(metrics).containsEntry("attempts", 100)
                .containsEntry("quota", 10)
                .containsEntry("successes", 10)
                .containsEntry("conflicts", 89)
                .containsEntry("duplicates", 1)
                .containsEntry("oversellCount", 0)
                .containsEntry("finalAvailable", 0)
                .containsEntry("finalReserved", 10)
                .containsEntry("redisMode", "DATABASE_FALLBACK");
        assertThat((Number) metrics.get("p50Millis")).isEqualTo(6L);
        assertThat((Number) metrics.get("p95Millis")).isEqualTo(10L);
    }

    @Test
    void rejectsOversellOrUnbalancedInventory() {
        assertThatThrownBy(() -> AppointmentLoadReport.buildReport(new AppointmentLoadReport.LoadResult(
                11, 10, 11, 0, 0, 1, 0, 11, 0, List.of(1L), "REDIS_LOCK")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oversell");
        assertThatThrownBy(() -> AppointmentLoadReport.buildReport(new AppointmentLoadReport.LoadResult(
                10, 10, 9, 1, 0, 0, 2, 9, 0, List.of(1L), "REDIS_LOCK")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventory");
    }

    @Test
    void writesJsonAndMarkdown(@TempDir Path output) throws Exception {
        AppointmentLoadReport.writeReport(output);

        assertThat(Files.readString(output.resolve("appointment-load.json")))
                .contains("fixtureVersion", "oversellCount", "p95Millis");
        assertThat(Files.readString(output.resolve("appointment-load.md")))
                .contains("Appointment load", "Oversell", "DATABASE_FALLBACK");
    }
}

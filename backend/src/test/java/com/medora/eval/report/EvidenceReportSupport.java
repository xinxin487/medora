package com.medora.eval.report;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class EvidenceReportSupport {
    public static final String FIXTURE_VERSION = "m6-v1";
    private static final Pattern FORBIDDEN_KEY = Pattern.compile(".*(token|password|secret|email|message|content).*");
    private static final ObjectMapper JSON = new ObjectMapper();

    private EvidenceReportSupport() { }

    public static Map<String, Object> report(String command, Map<String, Object> metrics,
                                             Map<String, Object> environment) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("gitCommit", gitCommit());
        report.put("fixtureVersion", FIXTURE_VERSION);
        report.put("featureFlags", Map.of(
                "contextBuilder", true,
                "layeredRag", true,
                "toolGateway", true,
                "a2uiAppointment", true,
                "mcp", true));
        report.put("command", command);
        report.put("metrics", Map.copyOf(metrics));
        report.put("environment", Map.copyOf(environment));
        validateSafe(report);
        return report;
    }

    public static Map<String, Object> deterministicEnvironment(String mode) {
        return Map.of(
                "javaVersion", Runtime.version().feature(),
                "osFamily", System.getProperty("os.name", "unknown"),
                "dataMode", mode);
    }

    public static void validateSafe(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (FORBIDDEN_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("forbidden report key: " + key);
                }
                validateSafe(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(EvidenceReportSupport::validateSafe);
        }
    }

    public static void writeJson(Path path, Map<String, Object> report) throws IOException {
        validateSafe(report);
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + System.lineSeparator());
    }

    public static String metadataMarkdown(Map<String, Object> report) {
        return "| Metadata | Value |\n|---|---|\n"
                + "| Generated at | " + report.get("generatedAt") + " |\n"
                + "| Git commit | `" + report.get("gitCommit") + "` |\n"
                + "| Fixture | `" + report.get("fixtureVersion") + "` |\n"
                + "| Command | `" + report.get("command") + "` |\n\n";
    }

    private static String gitCommit() {
        try {
            Process process = new ProcessBuilder(List.of("git", "rev-parse", "--short", "HEAD"))
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.waitFor() == 0 && !output.isBlank() ? output : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}

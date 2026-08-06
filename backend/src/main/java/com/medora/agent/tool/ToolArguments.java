package com.medora.agent.tool;

import java.time.LocalDate;
import java.util.Map;

public final class ToolArguments {

    private ToolArguments() {
    }

    public static String stringValue(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isBlank() ? null : normalized;
    }

    public static Integer intValue(Map<String, Object> arguments, String name, Integer fallback) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static String dateValue(Map<String, Object> arguments, String name) {
        String value = stringValue(arguments, name);
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value).toString();
    }
}

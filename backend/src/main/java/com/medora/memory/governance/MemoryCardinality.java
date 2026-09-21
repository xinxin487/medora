package com.medora.memory.governance;

public enum MemoryCardinality {
    SINGLE_VALUE, MULTI_VALUE;

    public static MemoryCardinality forType(String memoryType) {
        return switch (memoryType == null ? "" : memoryType) {
            case "PREFERRED_DEPARTMENT", "PREFERRED_DOCTOR", "PREFERRED_PERIOD", "LAST_SUCCESSFUL_APPOINTMENT" -> SINGLE_VALUE;
            default -> MULTI_VALUE;
        };
    }
}

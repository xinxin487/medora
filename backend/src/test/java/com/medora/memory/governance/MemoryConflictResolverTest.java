package com.medora.memory.governance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryConflictResolverTest {
    private final MemoryConflictResolver resolver = new MemoryConflictResolver();

    @Test void explicitNewPreferenceSupersedesOldPreference() {
        assertThat(resolver.resolve(MemoryCandidate.of("PREFERRED_PERIOD", "AM", "USER_EXPLICIT", .8),
                MemoryCandidate.of("PREFERRED_PERIOD", "PM", "USER_EXPLICIT", .8)))
                .isEqualTo(MemoryConflictResolver.Decision.REPLACE_EXISTING);
    }

    @Test void oneToolQueryCannotReplaceExplicitPreference() {
        assertThat(resolver.resolve(MemoryCandidate.of("PREFERRED_PERIOD", "AM", "USER_EXPLICIT", .8),
                MemoryCandidate.of("PREFERRED_PERIOD", "PM", "TOOL_QUERY", .3)))
                .isEqualTo(MemoryConflictResolver.Decision.KEEP_EXISTING);
    }
}

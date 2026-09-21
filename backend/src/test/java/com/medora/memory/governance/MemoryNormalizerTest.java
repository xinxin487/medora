package com.medora.memory.governance;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryNormalizerTest {
    private final MemoryNormalizer normalizer = new MemoryNormalizer();

    @ParameterizedTest
    @CsvSource({"上午,AM", "早上,AM", "午前,AM", "下午,PM"})
    void normalizesPeriodAliases(String raw, String expected) {
        assertThat(normalizer.normalize(MemoryCandidate.of("PREFERRED_PERIOD", raw)).normalizedValue())
                .isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void rejectsSymptomMemoryWithoutExpiry() {
        assertThatThrownBy(() -> normalizer.normalize(MemoryCandidate.of("RECENT_SYMPTOM", "持续咳嗽")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }
}

package com.medora.agent.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContextProfileResolverTest {
    private final ContextProfileResolver resolver = new ContextProfileResolver();

    @Test void bookingProfileUsesBusinessLanguageWithoutCallingLlm() {
        assertThat(resolver.resolve("帮我约明天下午呼吸内科", "GENERAL")).isEqualTo(ContextProfile.BOOKING);
    }

    @Test void ambiguousInputReturnsGeneral() {
        assertThat(resolver.resolve("你好", "GENERAL")).isEqualTo(ContextProfile.GENERAL);
    }
}

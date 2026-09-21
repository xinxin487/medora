package com.medora.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "medora.evolution.context-builder=true",
                    "medora.evolution.mcp=true"
            );

    @Test
    void bindsIndependentFlagsAndDefaultsUnsetFlagsToFalse() {
        runner.run(context -> {
            EvolutionProperties properties = context.getBean(EvolutionProperties.class);

            assertThat(properties.contextBuilder()).isTrue();
            assertThat(properties.layeredRag()).isFalse();
            assertThat(properties.toolGateway()).isFalse();
            assertThat(properties.a2uiAppointment()).isFalse();
            assertThat(properties.mcp()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvolutionProperties.class)
    static class TestConfig {
    }
}

package com.medora.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medora.evolution")
public record EvolutionProperties(
        boolean contextBuilder,
        boolean layeredRag,
        boolean toolGateway,
        boolean a2uiAppointment,
        boolean mcp) {
}

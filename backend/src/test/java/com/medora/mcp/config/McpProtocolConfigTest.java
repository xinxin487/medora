package com.medora.mcp.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class McpProtocolConfigTest {
    @Test void pinsTheLatestProtocolActuallySupportedByThePinnedSdk() {
        assertThat(McpProtocolConfig.PROTOCOL_VERSION).isEqualTo("2025-11-25");
    }
}

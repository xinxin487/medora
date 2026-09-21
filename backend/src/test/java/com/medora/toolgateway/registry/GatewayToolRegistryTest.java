package com.medora.toolgateway.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.toolgateway.descriptor.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayToolRegistryTest {
    @Test void findsVersionedDescriptorAndRejectsCollisions() {
        ToolDescriptor descriptor = descriptor("local.queryScheduleSlots", "1");
        GatewayToolRegistry registry = new GatewayToolRegistry(List.of(descriptor));
        assertThat(registry.find("local.queryScheduleSlots@1")).contains(descriptor);
        assertThatThrownBy(() -> new GatewayToolRegistry(List.of(descriptor, descriptor))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry.all().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    private ToolDescriptor descriptor(String name, String version) {
        return new ToolDescriptor(name, version, "test", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW,
                List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL");
    }
}

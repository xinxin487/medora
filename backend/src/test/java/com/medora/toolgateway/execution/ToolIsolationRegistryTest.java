package com.medora.toolgateway.execution;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ToolIsolationRegistryTest {
    @Test void rejectsSecondLeaseForSameToolUntilFirstIsReleased() {
        ToolIsolationRegistry registry = new ToolIsolationRegistry(1);
        ToolIsolationRegistry.Lease first = registry.tryAcquire("local.queryScheduleSlots@1");
        assertThat(first).isNotNull();
        assertThat(registry.tryAcquire("local.queryScheduleSlots@1")).isNull();
        first.close();
        assertThat(registry.tryAcquire("local.queryScheduleSlots@1")).isNotNull();
    }
}

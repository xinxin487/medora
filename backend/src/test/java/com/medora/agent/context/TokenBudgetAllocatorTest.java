package com.medora.agent.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TokenBudgetAllocatorTest {
    private final TokenBudgetAllocator allocator = new TokenBudgetAllocator();

    @Test void allocationNeverExceedsTotal() {
        ContextBudget budget = allocator.allocate(ContextProfile.KNOWLEDGE, 8_000);
        assertThat(budget.totalAllocated()).isLessThanOrEqualTo(8_000);
        assertThat(budget.rag()).isGreaterThan(budget.memory());
    }

    @Test void zeroBudgetStaysZero() {
        assertThat(allocator.allocate(ContextProfile.GENERAL, 0).totalAllocated()).isZero();
    }
}

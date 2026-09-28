package com.medora.memory.governance;

import com.medora.agent.context.ContextItem;
import com.medora.agent.context.ContextProfile;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySelectorTest {
    private final MemorySelector selector = new MemorySelector();

    @Test void rejectsExpiredAndDuplicateMemoriesWithReasons() {
        var expired = item("expired", "PREFERRED_PERIOD", "AM", OffsetDateTime.now().minusMinutes(1));
        var duplicate = item("summary-fingerprint", "PREFERRED_DEPARTMENT", "呼吸内科", null);

        var selection = selector.select(ContextProfile.BOOKING, List.of(expired, duplicate), 100, List.of("summary-fingerprint"));

        assertThat(selection.items()).isEmpty();
        assertThat(selection.decisions()).extracting(d -> d.reasonCode())
                .contains("EXPIRED", "DUPLICATE");
    }

    @Test void prefersExplicitMemoryAndenforcesBudget() {
        var explicit = new ContextItem("MEMORY", "PREFERRED_PERIOD", "explicit", "AM", .8, "USER_EXPLICIT", null, 60);
        var toolQuery = new ContextItem("MEMORY", "PREFERRED_PERIOD", "query", "PM", .3, "TOOL_QUERY", null, 60);
        var overflow = new ContextItem("MEMORY", "HISTORY", "overflow", "历史", .1, "TOOL_QUERY", null, 60);

        var selection = selector.select(ContextProfile.BOOKING, List.of(toolQuery, explicit, overflow), 60, List.of());

        assertThat(selection.items()).extracting(ContextItem::fingerprint).containsExactly("explicit");
        assertThat(selection.decisions()).extracting(d -> d.reasonCode()).contains("CONFLICT_LOSER", "BUDGET_EXCEEDED");
    }

    @Test void limitsHistoryMemoriesToTwoPerType() {
        var selection = selector.select(ContextProfile.BOOKING, List.of(
                item("history-1", "VISIT_HISTORY", "一月复诊", null),
                item("history-2", "VISIT_HISTORY", "二月复诊", null),
                item("history-3", "VISIT_HISTORY", "三月复诊", null)), 100, List.of());

        assertThat(selection.items()).hasSize(2);
        assertThat(selection.decisions()).extracting(d -> d.reasonCode()).contains("TYPE_LIMIT");
    }

    @Test void newerMemoryWinsWhenRelevanceConfidenceAndSourceAreEqual() {
        var old = new ContextItem("MEMORY", "PREFERRED_PERIOD", "old", "上午", .8,
                "USER_EXPLICIT", null, 20, OffsetDateTime.now().minusDays(150));
        var recent = new ContextItem("MEMORY", "PREFERRED_PERIOD", "recent", "下午", .8,
                "USER_EXPLICIT", null, 20, OffsetDateTime.now().minusDays(1));

        var selection = selector.select(ContextProfile.BOOKING, List.of(old, recent), 100, List.of());

        assertThat(selection.items()).extracting(ContextItem::fingerprint).containsExactly("recent");
    }

    private ContextItem item(String fingerprint, String type, String text, OffsetDateTime expiresAt) {
        return new ContextItem("MEMORY", type, fingerprint, text, .8, "USER_EXPLICIT", expiresAt, 20);
    }
}

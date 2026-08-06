package com.medora.adk.guard;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.medora.agent.tool.ToolNames;
import com.medora.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HighRiskToolBeforeCallback 单测 — 纯 schema 校验,不调 LLM。
 *
 * 验证:
 *  - bookAppointment 缺/空 slotId → 抛 BusinessException(不修改 args)
 *  - cancelAppointment 缺/空 appointmentId → 抛 BusinessException
 *  - 非 booking 工具 → 通过(返回 Optional.empty())
 *  - 合法参数 → 通过(返回 Optional.empty())
 */
class HighRiskToolBeforeCallbackTest {

    private HighRiskToolBeforeCallback callback;

    @BeforeEach
    void setUp() {
        callback = new HighRiskToolBeforeCallback();
    }

    @Test
    void bookAppointmentWithValidSlotIdPasses() {
        Optional<Map<String, Object>> result = callback.call(
                null, toolNamed(ToolNames.BOOK_APPOINTMENT),
                Map.of("slotId", "SLOT-12345"), null);

        assertThat(result).isEmpty();
    }

    @Test
    void bookAppointmentMissingSlotIdThrows() {
        assertThatThrownBy(() ->
                callback.call(null, toolNamed(ToolNames.BOOK_APPOINTMENT), Map.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slotId");
    }

    @Test
    void bookAppointmentBlankSlotIdThrows() {
        assertThatThrownBy(() ->
                callback.call(null, toolNamed(ToolNames.BOOK_APPOINTMENT),
                        Map.of("slotId", "  "), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slotId");
    }

    @Test
    void bookAppointmentNonStringSlotIdThrows() {
        assertThatThrownBy(() ->
                callback.call(null, toolNamed(ToolNames.BOOK_APPOINTMENT),
                        Map.of("slotId", 42), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void bookAppointmentNullArgsThrows() {
        assertThatThrownBy(() ->
                callback.call(null, toolNamed(ToolNames.BOOK_APPOINTMENT), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelAppointmentWithValidIdPasses() {
        Optional<Map<String, Object>> result = callback.call(
                null, toolNamed(ToolNames.CANCEL_APPOINTMENT),
                Map.of("appointmentId", "APT-20260623-AB"), null);

        assertThat(result).isEmpty();
    }

    @Test
    void cancelAppointmentMissingIdThrows() {
        assertThatThrownBy(() ->
                callback.call(null, toolNamed(ToolNames.CANCEL_APPOINTMENT), Map.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appointmentId");
    }

    @Test
    void nonBookingToolsPassThroughUnchanged() {
        Map<String, Object> args = new HashMap<>();
        args.put("keyword", "心内");

        Optional<Map<String, Object>> result = callback.call(
                null, toolNamed(ToolNames.QUERY_DEPARTMENTS), args, null);

        assertThat(result).isEmpty();
    }

    @Test
    void nullToolThrowsNpe() {
        assertThatThrownBy(() -> callback.call(null, null, Map.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static BaseTool toolNamed(String name) {
        BaseTool tool = mock(BaseTool.class);
        when(tool.name()).thenReturn(name);
        return tool;
    }
}

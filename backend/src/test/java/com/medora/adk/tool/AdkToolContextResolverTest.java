package com.medora.adk.tool;

import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import com.medora.agent.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * AdkToolContextResolver 单测:验证从 session state 正确解析 ToolExecutionContext。
 *
 * state() 在 ToolContext 上有两个重载(State 和 raw Map),Mockito 编译期类型推断
 * 会要求 State 类型,因此测试统一用 doReturn 绕过编译期类型检查。
 */
class AdkToolContextResolverTest {

    @Test
    void resolveWithAllFields() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of(
                AdkToolContextResolver.KEY_USER_ACCOUNT_ID, 42L,
                AdkToolContextResolver.KEY_USER_MESSAGE, "我想约心内科",
                AdkToolContextResolver.KEY_TRACE_ID, "trace-abc-123",
                AdkToolContextResolver.KEY_CONVERSATION_ID, "conversation-123"
        ));

        ToolExecutionContext result = AdkToolContextResolver.resolve(ctx);

        assertThat(result.userAccountId()).isEqualTo(42L);
        assertThat(result.userMessage()).isEqualTo("我想约心内科");
        assertThat(result.traceId()).isEqualTo("trace-abc-123");
        assertThat(result.conversationId()).isEqualTo("conversation-123");
    }

    @Test
    void resolveAcceptsIntegerAsLong() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of(
                AdkToolContextResolver.KEY_USER_ACCOUNT_ID, 100,
                AdkToolContextResolver.KEY_USER_MESSAGE, "",
                AdkToolContextResolver.KEY_TRACE_ID, "t1"
        ));

        ToolExecutionContext result = AdkToolContextResolver.resolve(ctx);
        assertThat(result.userAccountId()).isEqualTo(100L);
    }

    @Test
    void resolveAcceptsStringAsLong() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of(
                AdkToolContextResolver.KEY_USER_ACCOUNT_ID, "999",
                AdkToolContextResolver.KEY_USER_MESSAGE, "",
                AdkToolContextResolver.KEY_TRACE_ID, "t1"
        ));

        ToolExecutionContext result = AdkToolContextResolver.resolve(ctx);
        assertThat(result.userAccountId()).isEqualTo(999L);
    }

    @Test
    void failsWhenUserAccountIdMissing() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of());

        assertThatThrownBy(() -> AdkToolContextResolver.resolve(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdkToolContextResolver.KEY_USER_ACCOUNT_ID);
    }

    @Test
    void failsWhenStateIsNull() {
        ToolContext ctx = mock(ToolContext.class);
        doReturn(null).when(ctx).state();

        assertThatThrownBy(() -> AdkToolContextResolver.resolve(ctx))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenUserAccountIdIsNonNumeric() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of(
                AdkToolContextResolver.KEY_USER_ACCOUNT_ID, "not-a-number",
                AdkToolContextResolver.KEY_USER_MESSAGE, "",
                AdkToolContextResolver.KEY_TRACE_ID, "t1"
        ));

        assertThatThrownBy(() -> AdkToolContextResolver.resolve(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    void userMessageAndTraceIdNullable() {
        ToolContext ctx = mock(ToolContext.class);
        stubState(ctx, Map.of(
                AdkToolContextResolver.KEY_USER_ACCOUNT_ID, 1L
        ));

        ToolExecutionContext result = AdkToolContextResolver.resolve(ctx);
        assertThat(result.userAccountId()).isEqualTo(1L);
        assertThat(result.userMessage()).isNull();
        assertThat(result.traceId()).isNull();
    }

    @Test
    void nullToolContextThrows() {
        assertThatThrownBy(() -> AdkToolContextResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** 包装为 ADK State 类型(state() 返回 State 而非 raw Map),State 实现了 ConcurrentMap */
    private static void stubState(ToolContext ctx, Map<String, Object> state) {
        Map<String, Object> mutable = new HashMap<>(state);
        State adkState = new State(new ConcurrentHashMap<>(mutable));
        doReturn(adkState).when(ctx).state();
    }
}

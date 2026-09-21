package com.medora.adk.tool;

import com.google.adk.tools.ToolContext;
import com.medora.agent.tool.ToolExecutionContext;

import java.util.Map;
import java.util.Objects;

/**
 * 从 ADK ToolContext(其 state 来自 Session)解析出 Medora 的 ToolExecutionContext。
 *
 * ADK 的 Session state 是工具调用过程中持久化的 Map<String,Object>。我们在 Phase 9.3 的
 * MedoraSessionState 中会定义完整 schema,Phase 9.2 这里只取关键字段:
 *   - medora:userAccountId   Long   用户账号 ID(从 JWT 解出后由 controller 注入)
 *   - medora:userMessage     String 当前用户消息原文(LLM 可能改写,但工具需要原文做 RAG)
 *   - medora:traceId         String 请求追踪 ID(贯穿 AgentTrace)
 *
 * 字段缺失时:抛 IllegalStateException fail-fast,而不是返回 null 让下游 NPE。
 */
public final class AdkToolContextResolver {

    /** state key 前缀,避免与 ADK 内部 key(以 _ 开头)冲突 */
    public static final String KEY_USER_ACCOUNT_ID = "medora:userAccountId";
    public static final String KEY_USER_MESSAGE = "medora:userMessage";
    public static final String KEY_TRACE_ID = "medora:traceId";
    public static final String KEY_CONVERSATION_ID = "medora:conversationId";

    private AdkToolContextResolver() {
    }

    public static ToolExecutionContext resolve(ToolContext toolContext) {
        Objects.requireNonNull(toolContext, "toolContext");
        Map<String, Object> state = toolContext.state();
        Long userAccountId = readLong(state, KEY_USER_ACCOUNT_ID);
        String userMessage = readString(state, KEY_USER_MESSAGE);
        String traceId = readString(state, KEY_TRACE_ID);
        String conversationId = readString(state, KEY_CONVERSATION_ID);
        return new ToolExecutionContext(userAccountId, userMessage, traceId, conversationId);
    }

    private static Long readLong(Map<String, Object> state, String key) {
        Object value = state == null ? null : state.get(key);
        if (value == null) {
            throw new IllegalStateException("session state missing required key: " + key);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("session state key " + key + " is not a number: " + value);
        }
    }

    private static String readString(Map<String, Object> state, String key) {
        Object value = state == null ? null : state.get(key);
        return value == null ? null : value.toString();
    }
}

package com.medora.adk.sse;

import java.util.List;

/**
 * ADK Flowable&lt;Event&gt; 订阅结束后的聚合结果。
 *
 * 由 AdkEventToSseAdapter 在订阅期间累积,返回给 ChatOrchestrator 用于:
 *  - finalReply:用于 ChatMessageEntity 持久化 + AgentRunResult.reply()
 *  - toolEventsJson:写入 AgentTraceEntity.toolEventsJson(JSON 数组)
 *  - safetyEventsJson:写入 AgentTraceEntity.safetyEventsJson
 *  - hallucinationRisk:写入 AgentTraceEntity.hallucinationRisk
 *
 * 设计为不可变 record,字段全部允许为空集合/空字符串(由调用方处理)。
 */
public record AdkStreamResult(
        String finalReply,
        String toolEventsJson,
        String safetyEventsJson,
        String hallucinationRisk,
        List<String> transferChain
) {
    public static AdkStreamResult empty() {
        return new AdkStreamResult("", "[]", "[]", "LOW", List.of());
    }
}

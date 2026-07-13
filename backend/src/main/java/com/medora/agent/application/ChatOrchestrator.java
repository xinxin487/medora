package com.medora.agent.application;

import com.medora.adk.orchestrator.AdkOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 9.8 — Agent 编排统一入口,仅委托 ADK Orchestrator。
 *
 * <p>历史:Phase 0-9.7 期间本类包含 ~360 行过程式编排(意图路由、检索、工具执行、
 * prompt 构建、流式生成、安全审查、幻觉评估)。Phase 9.5 起 4-agent ADK 拓扑
 * (Root/Triage/Booking/Knowledge)逐步替代该路径,Phase 9.8 完成灰度切换并清理旧代码。
 *
 * <p>现存职责:取消检查 + 委托给 AdkOrchestrator。所有意图/检索/工具/安全/幻觉逻辑
 * 都由 ADK 内部 callback + agent 拓扑处理(RootRouterAgent / LlmInputSafetyBeforeCallback /
 * HighRiskToolBeforeCallback / LlmOutputSafetyAfterCallback / LlmHallucinationAfterCallback)。
 */
@Component
public class ChatOrchestrator {

    private final AdkOrchestrator adkOrchestrator;

    public ChatOrchestrator(AdkOrchestrator adkOrchestrator) {
        this.adkOrchestrator = adkOrchestrator;
    }

    public AgentRunResult handle(AgentChatCommand command, SseEmitter emitter, AtomicBoolean cancelled) throws IOException {
        if (cancelled.get()) {
            return abortResult();
        }
        return adkOrchestrator.orchestrate(command, emitter, cancelled);
    }

    private AgentRunResult abortResult() {
        return new AgentRunResult(
                new IntentDecision("CANCELLED", RouteMode.CHAT_ONLY, false, List.of()),
                "对话已被取消。",
                List.of(),
                List.of(),
                "[]",
                "{}",
                "{\"inputTokens\":0,\"outputTokens\":0}",
                "[]",
                "LOW",
                "[]"
        );
    }
}

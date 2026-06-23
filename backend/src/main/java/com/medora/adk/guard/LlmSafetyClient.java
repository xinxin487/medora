package com.medora.adk.guard;

import com.medora.agent.application.AgentModelGateway;
import com.medora.agent.policy.LlmCallTemplate;
import org.springframework.stereotype.Component;

/**
 * LLM 安全审查客户端 — 把 AgentModelGateway 的 Optional<String> 接口
 * 适配为「失败即抛 LlmUnavailableException」的接口,符合 LLM-first 原则。
 *
 * 现有 AgentModelGateway.lightweightLlmCall 在 provider=local 时返回 empty、
 * 在网络异常时也返回 empty。这些都视为「LLM 不可用」而非「内容安全」,
 * 由 LlmCallTemplate 转换为 LlmUnavailableException,统一返回「当前系统存在错误,
 * 请稍后尝试」。
 *
 * Phase 9.8 时会改写 AgentModelGateway 所有方法,届时本类可以直接调底层。
 */
@Component
public class LlmSafetyClient {

    private final AgentModelGateway agentModelGateway;

    public LlmSafetyClient(AgentModelGateway agentModelGateway) {
        this.agentModelGateway = agentModelGateway;
    }

    /**
     * 调用 LLM。空响应或异常一律抛 LlmUnavailableException。
     *
     * @param operationName 用于错误日志的诊断名(如 "inputSafetyCheck")
     */
    public String call(String operationName, String systemPrompt, String userPrompt) {
        return LlmCallTemplate.call(operationName,
                () -> agentModelGateway.lightweightLlmCall(systemPrompt, userPrompt)
                        .orElse(null));
    }
}

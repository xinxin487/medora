package com.medora.eval.llm;

/**
 * Phase 10 W4 — LLM 调用抽象(评测专用)。
 *
 * <p>所有需要 LLM 的评测组件(LlmJudge / AdversarialCaseGenerator 等)统一通过 LlmClient,
 * 这样可以在不同环境下替换实现而不影响评测逻辑:
 * <ul>
 *   <li>{@link RecordingLlmClient} — 包装真实 LLM,首次跑时把响应录下来</li>
 *   <li>{@link ReplayingLlmClient} — 从录制文件回放,无网络调用,CI 用</li>
 *   <li>测试桩 — 单测里直接返回固定字符串</li>
 * </ul>
 *
 * <p>请求/响应都是裸字符串,具体的 system/user prompt 由调用方组装。
 * 这是为了避免和 AgentModelGateway 的领域模型耦合,保持评测代码独立。
 */
public interface LlmClient {

    /**
     * 同步调用 LLM。
     *
     * @param systemPrompt 系统提示(可为 null)
     * @param userPrompt   用户消息
     * @return LLM 响应文本
     * @throws Exception 网络/解析/回放未命中等都抛异常(由调用方决定降级策略)
     */
    String complete(String systemPrompt, String userPrompt) throws Exception;

    /**
     * 标识此 client 的来源(用于日志/录制元数据)。
     * 例如 "recording:/eval/recordings/judge.json" / "replaying:..." / "stub"。
     */
    String describe();
}

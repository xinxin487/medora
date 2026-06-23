package com.medora.agent.policy;

/**
 * Agent 编排层的职责划分策略(单源真理,代码评审依据)。
 *
 * 详细说明见 design/08-phase-9-adk-refactor-plan.md「LLM-first 设计原则」。
 *
 * 核心原则:
 *  1. 决策层 / 语义判断 LLM-only,失败抛 LlmUnavailableException,不降级
 *  2. 格式校验 / 输入消毒 / 鉴权 保留确定性代码(正则、parser、crypto)
 *  3. 不允许 try { llm } catch { regex } 同职责双轨
 *
 * 评审红线:看到决策层的 try-catch-fallback,直接 reject。
 */
public final class LayerPolicy {

    private LayerPolicy() {}

    /** 决策层(必须 LLM-only) */
    public static final String[] LLM_ONLY_LAYERS = {
        "意图路由(RootRouterAgent.transfer)",
        "症状 → 科室语义映射(TriageAgent)",
        "记忆 / 偏好从自然语言提取",
        "对话摘要 / 确认事实提取",
        "幻觉检测 / 引用覆盖检测",
        "语义安全审查(角色劫持 / 指令覆盖 / 提示探查)"
    };

    /** 保留确定性代码的层(不是「假 agent」) */
    public static final String[] DETERMINISTIC_LAYERS = {
        "工具参数 schema 校验(类型、必填、UUID)",
        "日期 / 数字格式解析",
        "SQL / XSS / 代码注入的输入消毒(InputSanitizer)",
        "JWT / 认证 / 鉴权",
        "限流"
    };
}

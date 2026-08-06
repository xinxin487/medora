package com.medora.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * LLM 不可用时统一抛出此异常。
 *
 * 设计原则(见 design/08-phase-9-adk-refactor-plan.md「LLM-first 设计原则」):
 *  - 决策层 LLM 调用失败时,不允许降级到正则/规则,直接抛此异常
 *  - 全局 handler 返回静态消息「当前系统存在错误,请稍后尝试」,不暴露错误细节
 *  - 详细 cause 写日志,带 requestId
 */
public class LlmUnavailableException extends BusinessException {

    /** 对外暴露的统一消息,不区分超时/限流/内容过滤,避免泄露内部细节 */
    public static final String PUBLIC_MESSAGE = "当前系统存在错误,请稍后尝试";

    public LlmUnavailableException(String operation, Throwable cause) {
        super("LLM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, PUBLIC_MESSAGE, List.of());
        if (cause != null) {
            initCause(cause);
        }
        // operation 仅用于日志,不暴露给前端;由全局 handler 在日志中打印
        putOperationForLogging(operation);
    }

    private void putOperationForLogging(String operation) {
        // 通过自定义字段持有 operation,供 handler 写日志;不进入 API 响应
        // 用一个 transient-style 字段(扩展 BusinessException 不加新 API)
        this.operation = operation;
    }

    private String operation;

    public String operation() {
        return operation;
    }
}

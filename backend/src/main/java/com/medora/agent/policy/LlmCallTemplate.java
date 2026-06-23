package com.medora.agent.policy;

import com.medora.common.exception.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * LLM 调用模板:统一失败处理,不吞异常,不降级。
 *
 * 用法:
 *   String reply = LlmCallTemplate.call("planIntent", () -> {
 *       return agentModelGateway.rawComplete(prompt);  // 可能抛任何异常
 *   });
 *
 * 任何 Exception 或 null 返回 → 抛 LlmUnavailableException。
 * 已是 LlmUnavailableException → 原样透传,不二次包装。
 */
public final class LlmCallTemplate {

    private static final Logger log = LoggerFactory.getLogger(LlmCallTemplate.class);

    private LlmCallTemplate() {}

    /**
     * 执行一次 LLM 调用。
     *
     * @param operationName 仅用于日志识别,不暴露给前端
     * @param supplier      实际的 LLM 调用逻辑(可能抛任何异常)
     * @param <T>           返回类型
     * @return LLM 调用结果(非 null)
     * @throws LlmUnavailableException 任何失败时统一抛出
     */
    public static <T> T call(String operationName, Supplier<T> supplier) {
        try {
            T result = supplier.get();
            if (result == null) {
                log.error("LLM call '{}' returned null", operationName);
                throw new LlmUnavailableException(operationName + " returned null", null);
            }
            return result;
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM call '{}' failed: {}: {}", operationName,
                    e.getClass().getSimpleName(), e.getMessage());
            throw new LlmUnavailableException(operationName, e);
        }
    }
}

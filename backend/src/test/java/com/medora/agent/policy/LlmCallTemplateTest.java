package com.medora.agent.policy;

import com.medora.common.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmCallTemplate 单元测试 — 验证「失败统一抛 LlmUnavailableException,不吞异常,不降级」。
 */
class LlmCallTemplateTest {

    @Test
    void returnsValueWhenSupplierSucceeds() {
        String result = LlmCallTemplate.call("op", () -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void throwsWhenSupplierReturnsNull() {
        assertThatThrownBy(() -> LlmCallTemplate.call("op", () -> null))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("当前系统存在错误");
    }

    @Test
    void wrapsIoException() {
        assertThatThrownBy(() -> LlmCallTemplate.call("planIntent", throwing(() -> {
            throw new IOException("connection reset");
        })))
                .isInstanceOf(LlmUnavailableException.class)
                .satisfies(e -> {
                    LlmUnavailableException ex = (LlmUnavailableException) e;
                    assertThat(ex.operation()).isEqualTo("planIntent");
                    assertThat(ex.getCause()).isInstanceOf(IOException.class);
                    assertThat(ex.getCause()).hasMessageContaining("connection reset");
                });
    }

    @Test
    void wrapsTimeoutException() {
        assertThatThrownBy(() -> LlmCallTemplate.call("replyGen", throwing(() -> {
            throw new TimeoutException("read timeout 30s");
        })))
                .isInstanceOf(LlmUnavailableException.class)
                .satisfies(e -> {
                    LlmUnavailableException ex = (LlmUnavailableException) e;
                    assertThat(ex.operation()).isEqualTo("replyGen");
                    assertThat(ex.getCause()).isInstanceOf(TimeoutException.class);
                });
    }

    @Test
    void wrapsRuntimeException() {
        assertThatThrownBy(() -> LlmCallTemplate.call("op", () -> {
            throw new IllegalStateException("HTTP 502 from upstream");
        }))
                .isInstanceOf(LlmUnavailableException.class)
                .satisfies(e -> {
                    LlmUnavailableException ex = (LlmUnavailableException) e;
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void doesNotDoubleWrapLlmUnavailableException() {
        LlmUnavailableException original = new LlmUnavailableException("planIntent", new IOException("x"));
        assertThatThrownBy(() -> LlmCallTemplate.call("op", () -> {
            throw original;
        }))
                .isSameAs(original);   // 原样透传,不二次包装
    }

    @Test
    void publicMessageIsStaticRegardlessOfCauseType() {
        LlmUnavailableException ioEx = new LlmUnavailableException("op", new IOException("net error"));
        LlmUnavailableException timeoutEx = new LlmUnavailableException("op", new TimeoutException("slow"));
        LlmUnavailableException nullEx = new LlmUnavailableException("op", null);

        assertThat(ioEx.getMessage()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);
        assertThat(timeoutEx.getMessage()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);
        assertThat(nullEx.getMessage()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);

        // 关键:对外消息不暴露 cause 细节
        assertThat(ioEx.getMessage()).doesNotContain("net error");
        assertThat(timeoutEx.getMessage()).doesNotContain("slow");
    }

    // —— sneaky throw helpers:让 lambda 能抛 checked exception,绕过 Supplier<T> 的签名限制 ——

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> Supplier<T> throwing(ThrowingSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                return sneakyThrow(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T sneakyThrow(Throwable t) {
        return (T) sneakyThrow0(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
        throw (T) t;
    }
}

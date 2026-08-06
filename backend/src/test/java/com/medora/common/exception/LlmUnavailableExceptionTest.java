package com.medora.common.exception;

import com.medora.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 LlmUnavailableException 的语义和 handler 行为。
 *
 * 关键不变式(见 LLM-first 设计原则):
 *  - 对外消息永远是同一句「当前系统存在错误,请稍后尝试」,不暴露 cause 细节
 *  - HTTP 503
 *  - code = LLM_UNAVAILABLE
 *  - cause 完整保留,供日志/handler 写入
 */
class LlmUnavailableExceptionTest {

    @Test
    void publicMessageIsStaticAndDoesNotLeakCause() {
        LlmUnavailableException ex = new LlmUnavailableException(
                "planIntent", new IOException("connection reset by DeepSeek API"));

        assertThat(ex.getMessage()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);
        assertThat(ex.getMessage()).isEqualTo("当前系统存在错误,请稍后尝试");
        // 关键:cause 细节绝对不能出现在对外消息里
        assertThat(ex.getMessage()).doesNotContain("DeepSeek");
        assertThat(ex.getMessage()).doesNotContain("connection reset");
        assertThat(ex.getMessage()).doesNotContain("IOException");
    }

    @Test
    void httpStatusIs503() {
        LlmUnavailableException ex = new LlmUnavailableException("op", new RuntimeException("timeout"));

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void codeIsLlmUnavailable() {
        LlmUnavailableException ex = new LlmUnavailableException("op", new RuntimeException("x"));

        assertThat(ex.getCode()).isEqualTo("LLM_UNAVAILABLE");
    }

    @Test
    void causeIsPreservedForLogging() {
        IOException rootCause = new IOException("read timeout after 30000ms");
        LlmUnavailableException ex = new LlmUnavailableException("planIntent", rootCause);

        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.operation()).isEqualTo("planIntent");
    }

    @Test
    void nullCauseIsAllowed() {
        LlmUnavailableException ex = new LlmUnavailableException("rawReturnNull", null);

        assertThat(ex.getCause()).isNull();
        assertThat(ex.operation()).isEqualTo("rawReturnNull");
        assertThat(ex.getMessage()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);
    }

    @Test
    void handlerReturnsCorrectApiErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        LlmUnavailableException ex = new LlmUnavailableException(
                "planIntent", new IOException("connection reset"));

        ResponseEntity<ApiErrorResponse> response = handler.handleLlmUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(body.message()).isEqualTo(LlmUnavailableException.PUBLIC_MESSAGE);
        // 对外消息不暴露内部细节
        assertThat(body.message()).doesNotContain("connection reset");
    }
}

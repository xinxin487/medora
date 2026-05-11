package com.medora.chat.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Component
public class StreamEventPublisher {

    public void publishMessageStart(SseEmitter emitter, String messageId, String conversationId, String traceId)
            throws IOException {
        send(emitter, "message_start", Map.of(
                "messageId", messageId,
                "conversationId", conversationId,
                "traceId", traceId
        ));
    }

    public void publishDelta(SseEmitter emitter, String delta) throws IOException {
        send(emitter, "message_delta", Map.of("delta", delta));
    }

    public void publishMessageEnd(SseEmitter emitter, String messageId) throws IOException {
        send(emitter, "message_end", Map.of(
                "messageId", messageId,
                "finishReason", "stop",
                "usage", Map.of("inputTokens", 0, "outputTokens", 0),
                "status", "SUCCESS"
        ));
    }

    public void publishError(SseEmitter emitter, String code, String message) throws IOException {
        send(emitter, "error", Map.of(
                "code", code,
                "message", message
        ));
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}

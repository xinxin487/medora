package com.medora.adk.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.medora.chat.sse.StreamEventPublisher;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADK Event → SSE 协议适配器(Phase 9.7)。
 *
 * 订阅 runner.runAsync 返回的 Flowable&lt;Event&gt;,逐事件翻译为现有 SSE 协议:
 *
 *  | ADK Event                                       | SSE 事件                  |
 *  |-------------------------------------------------|---------------------------|
 *  | Part.text()                                     | message_delta(delta)      |
 *  | Part.functionCall()                             | tool_status STARTED       |
 *  | Part.functionResponse() success                 | tool_status SUCCESS       |
 *  | Part.functionResponse() failure                 | tool_status FAILED        |
 *  | Event.actions().transferToAgent                 | (累积到 transferChain)    |
 *
 * 不发布 message_start / message_end — 这两个由 ChatRuntimeService 在 handle() 前后发布,
 * 与现有协议一致(避免重复发送)。
 *
 * 订阅完成后返回 AdkStreamResult,内含 finalReply / toolEventsJson / safetyEventsJson
 * 供 ChatOrchestrator 构造 AgentRunResult。
 *
 * 取消语义:AtomicBoolean cancelled=true 时立即抛 UncheckedIOException 终止 Flowable 订阅。
 */
@Component
public class AdkEventToSseAdapter {

    private static final Logger log = LoggerFactory.getLogger(AdkEventToSseAdapter.class);

    private final StreamEventPublisher streamEventPublisher;
    private final ObjectMapper objectMapper;

    public AdkEventToSseAdapter(StreamEventPublisher streamEventPublisher, ObjectMapper objectMapper) {
        this.streamEventPublisher = streamEventPublisher;
        this.objectMapper = objectMapper;
    }

    public AdkStreamResult stream(Flowable<Event> events,
                                  SseEmitter emitter,
                                  AtomicBoolean cancelled) {
        StringBuilder reply = new StringBuilder();
        List<Map<String, Object>> toolEvents = new ArrayList<>();
        List<Map<String, Object>> safetyEvents = new ArrayList<>();
        List<String> transferChain = new ArrayList<>();
        // functionCall id → toolName,等对应 functionResponse 时配对
        Map<String, String> pendingToolCalls = new ConcurrentHashMap<>();

        events.blockingForEach(event -> {
            if (cancelled.get()) {
                throw new UncheckedIOException(new IOException("stream cancelled by client"));
            }
            processEvent(event, emitter, cancelled, reply, toolEvents, transferChain, pendingToolCalls);
        });

        return new AdkStreamResult(
                reply.toString(),
                toJson(toolEvents),
                toJson(safetyEvents),
                computeHallucinationRisk(toolEvents, reply.toString()),
                List.copyOf(transferChain)
        );
    }

    private void processEvent(Event event,
                              SseEmitter emitter,
                              AtomicBoolean cancelled,
                              StringBuilder reply,
                              List<Map<String, Object>> toolEvents,
                              List<String> transferChain,
                              Map<String, String> pendingToolCalls) {
        Optional<Content> contentOpt = event.content();
        if (contentOpt.isEmpty()) {
            return;
        }
        Optional<List<Part>> partsOpt = contentOpt.get().parts();
        if (partsOpt.isEmpty()) {
            return;
        }
        for (Part part : partsOpt.get()) {
            processPart(part, emitter, cancelled, reply, toolEvents, transferChain, pendingToolCalls);
        }
    }

    private void processPart(Part part,
                             SseEmitter emitter,
                             AtomicBoolean cancelled,
                             StringBuilder reply,
                             List<Map<String, Object>> toolEvents,
                             List<String> transferChain,
                             Map<String, String> pendingToolCalls) {
        // 文本 delta
        Optional<String> textOpt = part.text();
        if (textOpt.isPresent() && !textOpt.get().isEmpty()) {
            String delta = textOpt.get();
            reply.append(delta);
            publishDeltaSafely(emitter, delta, cancelled);
            return;
        }

        // FunctionCall(工具调用发起)
        Optional<FunctionCall> fcOpt = part.functionCall();
        if (fcOpt.isPresent()) {
            FunctionCall fc = fcOpt.get();
            String toolName = fc.name().orElse("unknown");
            String callId = fc.id().orElse(null);
            if (callId != null) {
                pendingToolCalls.put(callId, toolName);
            }
            publishToolStatusSafely(emitter, toolName, "STARTED", "正在执行工具", cancelled);
            return;
        }

        // FunctionResponse(工具调用返回)
        Optional<FunctionResponse> frOpt = part.functionResponse();
        if (frOpt.isPresent()) {
            FunctionResponse fr = frOpt.get();
            String toolName = fr.name().orElse("unknown");
            String callId = fr.id().orElse(null);
            if (callId != null) {
                pendingToolCalls.remove(callId);
            }
            Map<String, Object> response = fr.response().orElse(Map.of());
            boolean success = !response.containsKey("error");
            String status = success ? "SUCCESS" : "FAILED";
            String message = extractToolMessage(response, success);
            publishToolStatusSafely(emitter, toolName, status, message, cancelled);
            toolEvents.add(buildToolEvent(toolName, status, response, message));
        }
    }

    private String extractToolMessage(Map<String, Object> response, boolean success) {
        if (!success) {
            Object error = response.get("error");
            return error == null ? "工具执行失败" : error.toString();
        }
        // 优先用 summary 字段,其次 message
        Object summary = response.get("summary");
        if (summary != null) {
            return summary.toString();
        }
        Object message = response.get("message");
        return message == null ? "执行完成" : message.toString();
    }

    private Map<String, Object> buildToolEvent(String toolName,
                                               String status,
                                               Map<String, Object> response,
                                               String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("toolName", toolName);
        event.put("status", status);
        event.put("message", message);
        // 完整 response 也记下来,trace 调试需要
        event.put("response", response);
        return event;
    }

    private void publishDeltaSafely(SseEmitter emitter, String delta, AtomicBoolean cancelled) {
        try {
            streamEventPublisher.publishDelta(emitter, delta);
        } catch (IOException ex) {
            if (cancelled.get()) {
                throw new UncheckedIOException(new IOException("stream cancelled"));
            }
            throw new UncheckedIOException(ex);
        }
    }

    private void publishToolStatusSafely(SseEmitter emitter,
                                         String toolName,
                                         String status,
                                         String message,
                                         AtomicBoolean cancelled) {
        try {
            streamEventPublisher.publishToolStatus(emitter, toolName, status, message);
        } catch (IOException ex) {
            if (cancelled.get()) {
                throw new UncheckedIOException(new IOException("stream cancelled"));
            }
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Phase 9.7 简化版幻觉风险评估 — 仅根据是否有 retrieval + reply 长度启发式判断。
     * Phase 9.7 之后:LlmHallucinationAfterCallback 在 callback 内已经评分过,
     * 这里只是 fallback 兜底,真实评分由 callback 通过 Event.actions() 写入(待补)。
     */
    private String computeHallucinationRisk(List<Map<String, Object>> toolEvents, String reply) {
        boolean hasRetrieval = toolEvents.stream()
                .anyMatch(e -> "searchKnowledge".equals(e.get("toolName")));
        boolean hasLongReply = reply != null && reply.length() > 100;
        if (hasLongReply && !hasRetrieval && toolEvents.isEmpty()) {
            return "HIGH";
        }
        if (hasRetrieval && hasLongReply) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String toJson(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException ex) {
            log.warn("failed to serialize list, falling back to []", ex);
            return "[]";
        }
    }

    /** 调试用 — 把单个 Event 解析为可读 string(不入 SSE,只在 log 里用) */
    public String debugEventToString(Event event) {
        if (event == null || event.content().isEmpty()) {
            return "{}";
        }
        Map<String, Object> summary = new HashMap<>();
        event.content().ifPresent(c -> c.parts().ifPresent(parts -> {
            List<String> partTypes = new ArrayList<>();
            for (Part p : parts) {
                if (p.text().isPresent()) {
                    partTypes.add("text:" + p.text().get().substring(0, Math.min(40, p.text().get().length())));
                } else if (p.functionCall().isPresent()) {
                    partTypes.add("call:" + p.functionCall().get().name().orElse("?"));
                } else if (p.functionResponse().isPresent()) {
                    partTypes.add("resp:" + p.functionResponse().get().name().orElse("?"));
                }
            }
            summary.put("parts", partTypes);
        }));
        summary.put("author", event.author());
        summary.put("turnComplete", event.turnComplete().orElse(false));
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}

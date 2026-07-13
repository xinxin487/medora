package com.medora.adk.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.medora.agent.application.AgentModelGateway;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.common.metrics.MedoraMetricsService;
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
    private final AgentModelGateway agentModelGateway;
    private final MedoraMetricsService metricsService;

    public AdkEventToSseAdapter(StreamEventPublisher streamEventPublisher,
                                ObjectMapper objectMapper,
                                AgentModelGateway agentModelGateway,
                                MedoraMetricsService metricsService) {
        this.streamEventPublisher = streamEventPublisher;
        this.objectMapper = objectMapper;
        this.agentModelGateway = agentModelGateway;
        this.metricsService = metricsService;
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

        // Fallback:LLM 调了工具但没生成总结文本(DeepSeek 等模型常见行为,看到工具 success
        // 直接 finishReason=STOP)。先用 LLM 基于工具结果生成自然语言总结;LLM 失败再退化到
        // 模板兜底,确保前端不会看到空白。
        String finalReply = reply.toString();
        if ((finalReply == null || finalReply.isBlank()) && !toolEvents.isEmpty()) {
            String llmSummary = generateLlmSummary(toolEvents);
            String fallback = llmSummary != null ? llmSummary : buildFallbackReply(toolEvents);
            String source = llmSummary != null ? "LLM" : "TEMPLATE";
            if (fallback != null) {
                log.info("AdkEventToSseAdapter fallback reply triggered. toolEvents={}, fallbackLen={}, source={}",
                        toolEvents.size(), fallback.length(), source);
                for (String delta : splitDeltas(fallback)) {
                    publishDeltaSafely(emitter, delta, cancelled);
                }
                finalReply = fallback;
            } else {
                log.warn("AdkEventToSseAdapter reply empty after tool calls but no fallback could be built. toolEvents={}",
                        toolEvents.size());
            }
        }

        return new AdkStreamResult(
                finalReply,
                toJson(toolEvents),
                toJson(safetyEvents),
                computeHallucinationRisk(toolEvents, finalReply),
                List.copyOf(transferChain)
        );
    }

    /**
     * 用 LLM 基于工具执行结果生成自然语言总结。
     *
     * 触发场景:DeepSeek 等模型调完工具直接 finishReason=STOP,没产出面向用户的文字。
     * 这时把 toolEvents 序列化喂给 LLM,让它生成一段简洁中文回复。
     *
     * 失败时返回 null,由上层 {@link #buildFallbackReply} 走模板兜底。
     */
    private String generateLlmSummary(List<Map<String, Object>> toolEvents) {
        try {
            String toolJson = objectMapper.writeValueAsString(toolEvents);
            String systemPrompt = """
                    你是 Medora 医院助手。后台 Agent 刚执行完一轮工具调用,但还没向用户用自然语言说明结果。
                    请基于下方工具执行结果(JSON),用简洁、自然的中文向用户说明:
                      - 调用了哪些工具、查到了什么 / 操作结果如何
                      - 关键字段(科室名、医生名、号源日期时段、预约号、费用、状态等)
                      - 如果有可选项,引导用户下一步该提供什么(如选择 slotId / 确认 appointmentId)

                    约束:
                      - 不要复述 JSON,不要解释"我是 AI 助手"
                      - 不要编造工具结果之外的信息;缺信息时如实告知并反问
                      - 业务操作成功必须明确告知(预约号、取消状态等)
                      - 多个工具结果按时间顺序合并,不要逐条罗列 JSON
                    """;
            String userPrompt = "工具执行结果:\n" + toolJson;
            Optional<String> result = agentModelGateway.lightweightLlmCall(systemPrompt, userPrompt);
            if (result.isEmpty()) {
                log.warn("AdkEventToSseAdapter LLM summary returned empty.");
                return null;
            }
            String summary = result.get().trim();
            return summary.isBlank() ? null : summary;
        } catch (Exception ex) {
            log.warn("AdkEventToSseAdapter LLM summary failed, will fall back to template.", ex);
            return null;
        }
    }

    /**
     * 当 LLM 工具调用后没输出文本时,从 toolEvents 构造兜底回复。
     *
     * 优先级:
     *  1. 最后一个 bookAppointment SUCCESS → 从 data 提取 appointmentNo/科室/医生/日期/时段
     *  2. 最后一个 cancelAppointment SUCCESS → 从 data 提取 appointmentNo
     *  3. 其他 SUCCESS → 拼接所有 SUCCESS 的 message
     *  4. 全失败 → 返回 null(让上层 warn 日志接管,前端会显示空但 trace 留痕)
     */
    private String buildFallbackReply(List<Map<String, Object>> toolEvents) {
        for (int i = toolEvents.size() - 1; i >= 0; i--) {
            Map<String, Object> e = toolEvents.get(i);
            if (!"SUCCESS".equals(String.valueOf(e.get("status")))) continue;
            String toolName = String.valueOf(e.get("toolName"));
            Object data = extractDataField(e);
            if ("bookAppointment".equals(toolName) && data instanceof Map map) {
                String built = formatBookSuccess(map);
                if (built != null) return built;
            }
            if ("cancelAppointment".equals(toolName) && data instanceof Map map) {
                String built = formatCancelSuccess(map);
                if (built != null) return built;
            }
        }
        List<String> msgs = new ArrayList<>();
        for (Map<String, Object> e : toolEvents) {
            if (!"SUCCESS".equals(String.valueOf(e.get("status")))) continue;
            Object m = e.get("message");
            if (m != null && !String.valueOf(m).isBlank()) msgs.add(String.valueOf(m));
        }
        if (msgs.isEmpty()) return null;
        return "已完成:" + String.join(";", msgs);
    }

    @SuppressWarnings("unchecked")
    private Object extractDataField(Map<String, Object> toolEvent) {
        Object response = toolEvent.get("response");
        if (!(response instanceof Map)) return null;
        return ((Map<String, Object>) response).get("data");
    }

    @SuppressWarnings("unchecked")
    private String formatBookSuccess(Map<String, Object> data) {
        String appointmentNo = str(data.get("appointmentNo"));
        Map<String, Object> dept = nested(data, "department");
        Map<String, Object> doctor = nested(data, "doctor");
        Map<String, Object> schedule = nested(data, "schedule");
        StringBuilder sb = new StringBuilder("已为您预约成功!");
        if (!appointmentNo.isBlank()) sb.append(" 预约号:").append(appointmentNo).append("。");
        if (dept != null && !str(dept.get("name")).isBlank()) sb.append(" 科室:").append(dept.get("name")).append("。");
        if (doctor != null && !str(doctor.get("name")).isBlank()) sb.append(" 医生:").append(doctor.get("name")).append("。");
        if (schedule != null) {
            String date = str(schedule.get("scheduleDate"));
            String period = str(schedule.get("periodCode"));
            if (!date.isBlank()) sb.append(" 日期:").append(date).append("。");
            if (!period.isBlank()) sb.append(" 时段:").append(periodLabel(period)).append("。");
        }
        return sb.length() > "已为您预约成功!".length() ? sb.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String formatCancelSuccess(Map<String, Object> data) {
        String appointmentNo = str(data.get("appointmentNo"));
        if (appointmentNo.isBlank()) return null;
        return "已为您取消预约,预约号:" + appointmentNo + "。";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String periodLabel(String code) {
        if (code == null) return "";
        return switch (code) {
            case "AM" -> "上午";
            case "PM" -> "下午";
            case "EVENING" -> "夜间";
            default -> code;
        };
    }

    private List<String> splitDeltas(String reply) {
        return List.of(reply.split("(?<=[。！？；\\n])"));
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
            metricsService.recordToolCall(toolName, status);
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
        // emitter == null 表示无 SSE 输出(eval live 模式 / orchestrateOneShot 调试入口),
        // 此时只累积 reply,不向前端推送。
        if (emitter == null) return;
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
        if (emitter == null) return;
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

package com.medora.adk.sse;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 9.7 AdkEventToSseAdapter 单测 — 不调真实 Runner / SseEmitter,
 * 用 Flowable.just(...) 喂入预设 Event 序列,验证 SSE 翻译逻辑。
 *
 * 覆盖场景:
 *  - 纯文本事件 → publishDelta + finalReply 拼接
 *  - FunctionCall → publishToolStatus(STARTED)
 *  - FunctionResponse success → publishToolStatus(SUCCESS) + toolEvents 累积
 *  - FunctionResponse failure → publishToolStatus(FAILED) + toolEvents 累积
 *  - 取消语义:cancelled=true 抛 UncheckedIOException
 *  - 空事件 / 空 parts → 安全跳过
 *  - hallucinationRisk 启发式(纯文本长回复 + 无工具调用 = HIGH)
 *  - toolEventsJson 序列化为正确 JSON 数组
 */
class AdkEventToSseAdapterTest {

    private StreamEventPublisher publisher;
    private AgentModelGateway agentModelGateway;
    private MedoraMetricsService metricsService;
    private AdkEventToSseAdapter adapter;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() throws IOException {
        publisher = mock(StreamEventPublisher.class);
        agentModelGateway = mock(AgentModelGateway.class);
        metricsService = mock(MedoraMetricsService.class);
        // 默认让 LLM 调用返回空,触发模板兜底;个别测试需要 LLM 时再 stub
        when(agentModelGateway.lightweightLlmCall(anyString(), anyString())).thenReturn(Optional.empty());
        adapter = new AdkEventToSseAdapter(publisher, new ObjectMapper(), agentModelGateway, metricsService);
        emitter = mock(SseEmitter.class);
    }

    private Event textEvent(String text) {
        return Event.builder()
                .id("evt-" + System.nanoTime())
                .content(Content.builder()
                        .role("model")
                        .parts(Part.fromText(text))
                        .build())
                .build();
    }

    private Event functionCallEvent(String toolName, String callId, Map<String, Object> args) {
        return Event.builder()
                .id("evt-call-" + System.nanoTime())
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .functionCall(FunctionCall.builder()
                                        .name(toolName)
                                        .id(callId)
                                        .args(args)
                                        .build())
                                .build()))
                        .build())
                .build();
    }

    private Event functionResponseEvent(String toolName, String callId, Map<String, Object> response) {
        return Event.builder()
                .id("evt-resp-" + System.nanoTime())
                .content(Content.builder()
                        .role("user") // function responses come back as user role in ADK
                        .parts(List.of(Part.builder()
                                .functionResponse(FunctionResponse.builder()
                                        .name(toolName)
                                        .id(callId)
                                        .response(response)
                                        .build())
                                .build()))
                        .build())
                .build();
    }

    private Event emptyContentEvent() {
        return Event.builder()
                .id("evt-empty")
                .build();
    }

    @Test
    void streamTranslatesTextPartsToDeltasAndConcatenatesReply() throws IOException {
        Flowable<Event> events = Flowable.just(textEvent("你好"), textEvent(","), textEvent("请稍等。"));

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.finalReply()).isEqualTo("你好,请稍等。");
        verify(publisher, atLeastOnce()).publishDelta(eq(emitter), eq("你好"));
        verify(publisher, atLeastOnce()).publishDelta(eq(emitter), eq(","));
        verify(publisher, atLeastOnce()).publishDelta(eq(emitter), eq("请稍等。"));
    }

    @Test
    void streamTranslatesFunctionCallToStartedToolStatus() throws IOException {
        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDepartments", "call-1", Map.of("keyword", "呼吸")));

        adapter.stream(events, emitter, new AtomicBoolean(false));

        verify(publisher).publishToolStatus(eq(emitter), eq("queryDepartments"), eq("STARTED"), anyString());
    }

    @Test
    void streamTranslatesSuccessfulFunctionResponseToSuccessToolStatus() throws IOException {
        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDepartments", "call-1", Map.of()),
                functionResponseEvent("queryDepartments", "call-1", Map.of(
                        "summary", "找到 3 个科室", "items", List.of()))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        verify(publisher).publishToolStatus(eq(emitter), eq("queryDepartments"), eq("STARTED"), anyString());
        verify(publisher).publishToolStatus(eq(emitter), eq("queryDepartments"), eq("SUCCESS"), anyString());
        assertThat(result.toolEventsJson()).contains("queryDepartments");
        assertThat(result.toolEventsJson()).contains("SUCCESS");
    }

    @Test
    void streamTranslatesFailedFunctionResponseToFailedToolStatus() throws IOException {
        Flowable<Event> events = Flowable.just(
                functionCallEvent("bookAppointment", "call-2", Map.of()),
                functionResponseEvent("bookAppointment", "call-2", Map.of(
                        "error", "slotId 不能为空", "code", "BUSINESS_ERROR"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        verify(publisher).publishToolStatus(eq(emitter), eq("bookAppointment"), eq("FAILED"), anyString());
        assertThat(result.toolEventsJson()).contains("FAILED");
        assertThat(result.toolEventsJson()).contains("slotId 不能为空");
    }

    @Test
    void streamSkipsEventsWithEmptyContent() {
        Flowable<Event> events = Flowable.just(emptyContentEvent(), emptyContentEvent());

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.finalReply()).isEmpty();
        assertThat(result.toolEventsJson()).isEqualTo("[]");
    }

    @Test
    void streamThrowsWhenCancelledMidStream() throws IOException {
        // 第一个 publishDelta 之后 cancelled = true
        doThrow(new IOException("simulated cancel"))
                .when(publisher).publishDelta(eq(emitter), anyString());

        Flowable<Event> events = Flowable.just(
                textEvent("first"),
                textEvent("second")); // 不应该到这里

        AtomicBoolean cancelled = new AtomicBoolean(true);
        assertThatThrownBy(() -> adapter.stream(events, emitter, cancelled))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void streamReturnsHighRiskWhenLongReplyWithoutRetrievalOrTool() {
        StringBuilder longReply = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longReply.append("这是一段很长的医学建议内容。");
        }

        Flowable<Event> events = Flowable.just(textEvent(longReply.toString()));

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.hallucinationRisk()).isEqualTo("HIGH");
    }

    @Test
    void streamReturnsLowRiskWhenToolCallsPresent() {
        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDepartments", "call-1", Map.of()),
                functionResponseEvent("queryDepartments", "call-1", Map.of("summary", "ok"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.hallucinationRisk()).isEqualTo("LOW");
    }

    @Test
    void streamReturnsMediumRiskWhenRetrievalButLongReply() {
        StringBuilder longReply = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            longReply.append("一段内容。");
        }

        Flowable<Event> events = Flowable.just(
                functionCallEvent("searchKnowledge", "call-r", Map.of()),
                functionResponseEvent("searchKnowledge", "call-r", Map.of("summary", "找到")),
                textEvent(longReply.toString())
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.hallucinationRisk()).isEqualTo("MEDIUM");
    }

    @Test
    void streamSerializesToolEventsAsValidJsonArray() {
        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDoctors", "call-1", Map.of()),
                functionResponseEvent("queryDoctors", "call-1", Map.of("summary", "3 个医生")),
                functionCallEvent("queryScheduleSlots", "call-2", Map.of()),
                functionResponseEvent("queryScheduleSlots", "call-2", Map.of("summary", "5 个号源"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.toolEventsJson()).startsWith("[").endsWith("]");
        // 至少含两个工具调用
        assertThat(result.toolEventsJson()).contains("queryDoctors");
        assertThat(result.toolEventsJson()).contains("queryScheduleSlots");
    }

    @Test
    void streamDoesNotPublishMessageStartOrEnd() throws IOException {
        // message_start / message_end 由 ChatRuntimeService 在 handle() 前后发布
        // adapter 不应该重复发,否则前端会解析两次
        Flowable<Event> events = Flowable.just(textEvent("hello"));

        adapter.stream(events, emitter, new AtomicBoolean(false));

        // publisher 没有 publishMessageStart / publishMessageEnd 方法被调用
        // (mock 不会自动校验,通过 verify never 这些方法的调用确认)
        verify(publisher, never()).publishMessageStart(any(), anyString(), anyString(), anyString());
        verify(publisher, never()).publishMessageEnd(any(), anyString());
    }

    @Test
    void streamEmptyFlowableReturnsEmptyResult() {
        AdkStreamResult result = adapter.stream(Flowable.empty(), emitter, new AtomicBoolean(false));

        assertThat(result.finalReply()).isEmpty();
        assertThat(result.toolEventsJson()).isEqualTo("[]");
        assertThat(result.safetyEventsJson()).isEqualTo("[]");
        assertThat(result.hallucinationRisk()).isEqualTo("LOW");
        assertThat(result.transferChain()).isEmpty();
    }

    @Test
    void streamAcceptsMixedTextAndToolEventsInOrder() throws IOException {
        Flowable<Event> events = Flowable.just(
                textEvent("正在为您查询。"),
                functionCallEvent("queryDepartments", "c1", Map.of()),
                functionResponseEvent("queryDepartments", "c1", Map.of("summary", "ok")),
                textEvent("找到呼吸科,正在查询医生。"),
                functionCallEvent("queryDoctors", "c2", Map.of()),
                functionResponseEvent("queryDoctors", "c2", Map.of("summary", "ok"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        ArgumentCaptor<String> deltaCaptor = ArgumentCaptor.forClass(String.class);
        verify(publisher, atLeastOnce()).publishDelta(eq(emitter), deltaCaptor.capture());
        List<String> deltas = deltaCaptor.getAllValues();
        assertThat(deltas).contains("正在为您查询。", "找到呼吸科,正在查询医生。");
        assertThat(result.finalReply()).contains("正在为您查询。");
        assertThat(result.finalReply()).contains("找到呼吸科");
    }

    @Test
    void streamUsesLlmSummaryWhenModelStopsAfterToolCall() throws IOException {
        // 模拟 DeepSeek 行为:调完工具直接 STOP,没产出文本
        when(agentModelGateway.lightweightLlmCall(anyString(), anyString()))
                .thenReturn(Optional.of("已为您查询到 3 个呼吸内科医生,请问您想预约哪一位?"));

        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDoctors", "c1", Map.of()),
                functionResponseEvent("queryDoctors", "c1", Map.of("summary", "3 个医生"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.finalReply()).isEqualTo("已为您查询到 3 个呼吸内科医生,请问您想预约哪一位?");
        verify(agentModelGateway).lightweightLlmCall(anyString(), anyString());
    }

    @Test
    void streamFallsBackToTemplateWhenLlmReturnsEmpty() throws IOException {
        // LLM 调用失败 / 返回空 → 退化到模板兜底,不能让前端看到空白
        when(agentModelGateway.lightweightLlmCall(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Flowable<Event> events = Flowable.just(
                functionCallEvent("queryDoctors", "c1", Map.of()),
                functionResponseEvent("queryDoctors", "c1", Map.of("summary", "3 个医生"))
        );

        AdkStreamResult result = adapter.stream(events, emitter, new AtomicBoolean(false));

        assertThat(result.finalReply()).startsWith("已完成:");
        assertThat(result.finalReply()).contains("3 个医生");
    }

    /**
     * Phase 10 W4.5 — null emitter 路径(评测 live 模式 + orchestrateOneShot 调试入口)。
     *
     * <p>不能 NPE、不能调 publisher.publishDelta / publishToolStatus,
     * 但要把 reply / toolEvents / transferChain 正常累积到 AdkStreamResult。
     */
    @Test
    void streamToleratesNullEmitterForEvalAndOneShot() throws IOException {
        Flowable<Event> events = Flowable.just(
                textEvent("你好"),
                functionCallEvent("queryDepartments", "c1", Map.of("keyword", "呼吸")),
                functionResponseEvent("queryDepartments", "c1", Map.of("summary", "呼吸内科"))
        );

        AdkStreamResult result = adapter.stream(events, null, new AtomicBoolean(false));

        assertThat(result.finalReply()).isEqualTo("你好");
        assertThat(result.toolEventsJson()).contains("queryDepartments");
        // 关键:publisher 永远没被调用,因为 emitter == null
        verifyNoInteractions(publisher);
    }
}

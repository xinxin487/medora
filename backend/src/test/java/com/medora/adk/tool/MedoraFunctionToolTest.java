package com.medora.adk.tool;

import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRegistry;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MedoraFunctionTool 单测:验证 BaseTool 桥接行为正确。
 *
 * 重点测试:
 *  - declaration() 正确暴露 FunctionDeclaration(name/description/parameters)
 *  - runAsync 成功路径:toolDefinition.execute 被调用,返回值序列化为 SUCCESS 结构
 *  - runAsync 失败路径:toolDefinition 抛 RuntimeException 时,返回 FAILED 结构(不传播异常)
 *  - runAsync 失败路径:toolDefinition 抛 BusinessException 时,保留 errorCode/errorMessage
 *  - args null 容错:runAsync(null, ctx) 不抛 NPE
 *  - create() 工厂方法:从 registry 拿到 ToolDefinition,包装成功
 */
class MedoraFunctionToolTest {

    @Test
    void declarationExposesNameDescriptionAndParameters() {
        ToolDefinition def = stubDefinition(ToolNames.QUERY_DEPARTMENTS, "查询可挂号科室");
        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.QUERY_DEPARTMENTS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Optional<FunctionDeclaration> fdOpt = tool.declaration();

        assertThat(fdOpt).isPresent();
        FunctionDeclaration fd = fdOpt.get();
        assertThat(fd.name()).isEqualTo(Optional.of(ToolNames.QUERY_DEPARTMENTS));
        assertThat(fd.description()).isEqualTo(Optional.of("查询可挂号科室"));
        assertThat(fd.parameters()).isPresent();
        Schema params = fd.parameters().get();
        assertThat(params.type()).isPresent();
        assertThat(params.type().get().knownEnum()).isEqualTo(Type.Known.OBJECT);
        assertThat(params.properties().get()).containsKeys("keyword", "pageSize");
    }

    @Test
    void nameAndDescriptionFromBaseToolConstructor() {
        ToolDefinition def = stubDefinition(ToolNames.SEARCH_KNOWLEDGE, "检索已发布知识库");
        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.SEARCH_KNOWLEDGE);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        assertThat(tool.name()).isEqualTo(ToolNames.SEARCH_KNOWLEDGE);
        assertThat(tool.description()).isEqualTo("检索已发布知识库");
    }

    @Test
    void runAsyncSuccessSerializesResult() throws Throwable {
        ToolDefinition def = mock(ToolDefinition.class);
        Object data = Map.of("list", java.util.List.of());
        ToolExecutionResult successResult = ToolExecutionResult.success(
                ToolNames.QUERY_DEPARTMENTS, data, "已查询到 0 个科室。");
        when(def.execute(anyMap(), any(ToolExecutionContext.class))).thenReturn(successResult);

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.QUERY_DEPARTMENTS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> args = Map.of("keyword", "心内");
        Single<Map<String, Object>> result = tool.runAsync(args, stubToolContext());

        Map<String, Object> output = result.blockingGet();
        assertThat(output.get("status")).isEqualTo("SUCCESS");
        assertThat(output.get("summary")).isEqualTo("已查询到 0 个科室。");
        assertThat(output).doesNotContainKey("errorCode");
    }

    /**
     * 防回归:工具返回包含 OffsetDateTime 的 record(如 AppointmentItem.bookedAt)时,
     * 必须能被 ADK 内部 ObjectMapper 序列化。修复前会抛 InvalidDefinitionException。
     *
     * 这里通过把序列化后的 data 重新 writeValueAsString 来验证 —— 如果 data 还是 raw Java 对象,
     * 一个没注册 JavaTimeModule 的默认 ObjectMapper 会失败;修复后 data 已经是 JsonNode,
     * 任何 ObjectMapper 都能写。
     */
    @Test
    void runAsyncNormalizesOffsetDateTimeInDataForAdkSerialization() throws Exception {
        ToolDefinition def = mock(ToolDefinition.class);
        java.time.OffsetDateTime bookedAt = java.time.OffsetDateTime.parse("2026-06-29T10:30:00+08:00");
        Object data = java.util.List.of(Map.of(
                "appointmentId", "APT-1",
                "bookedAt", bookedAt
        ));
        ToolExecutionResult successResult = ToolExecutionResult.success(
                ToolNames.GET_USER_APPOINTMENTS, data, "已查询到 1 条预约记录。");
        when(def.execute(anyMap(), any(ToolExecutionContext.class))).thenReturn(successResult);

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.GET_USER_APPOINTMENTS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> output = tool.runAsync(Map.of(), stubToolContext()).blockingGet();

        // 模拟 ADK LangChain4j.toJson —— 用一个 *没有* JavaTimeModule 的默认 ObjectMapper 重写
        com.fasterxml.jackson.databind.ObjectMapper adkBareMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        String json = adkBareMapper.writeValueAsString(output);
        assertThat(json).contains("APT-1");
        assertThat(json).contains("2026-06-29T10:30:00");
        assertThat(json).contains("已查询到 1 条预约记录");
    }

    @Test
    void runAsyncWithBusinessExceptionPreservesErrorCode() {
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.execute(anyMap(), any(ToolExecutionContext.class)))
                .thenThrow(new com.medora.common.exception.BusinessException(
                        "AGENT_TOOL_ARGUMENT_MISSING",
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "查询号源需要明确日期"
                ));

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.QUERY_SCHEDULE_SLOTS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> output = tool.runAsync(Map.of(), stubToolContext()).blockingGet();

        assertThat(output.get("status")).isEqualTo("FAILED");
        assertThat(output.get("errorCode")).isEqualTo("AGENT_TOOL_ARGUMENT_MISSING");
        assertThat(output.get("errorMessage")).isEqualTo("查询号源需要明确日期");
    }

    @Test
    void runAsyncWithRuntimeExceptionReturnsGenericFailure() {
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.execute(anyMap(), any(ToolExecutionContext.class)))
                .thenThrow(new IllegalStateException("boom"));

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.QUERY_DEPARTMENTS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> output = tool.runAsync(Map.of(), stubToolContext()).blockingGet();

        assertThat(output.get("status")).isEqualTo("FAILED");
        assertThat(output.get("errorCode")).isEqualTo("IllegalStateException");
        assertThat(output.get("errorMessage")).isEqualTo("boom");
    }

    @Test
    void runAsyncHandlesNullArgs() {
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.execute(anyMap(), any(ToolExecutionContext.class)))
                .thenReturn(ToolExecutionResult.success(ToolNames.SEARCH_KNOWLEDGE, Map.of(), "ok"));

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.SEARCH_KNOWLEDGE);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> output = tool.runAsync(null, stubToolContext()).blockingGet();
        assertThat(output.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    void runAsyncForwardsArgsToToolDefinition() {
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.execute(anyMap(), any(ToolExecutionContext.class)))
                .thenReturn(ToolExecutionResult.success(ToolNames.QUERY_DOCTORS, Map.of(), "ok"));

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.QUERY_DOCTORS);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> args = new HashMap<>();
        args.put("keyword", "张");
        args.put("departmentId", "DEPT-1");
        tool.runAsync(args, stubToolContext()).blockingGet();

        verify(def).execute(eq(args), any(ToolExecutionContext.class));
    }

    @Test
    void createBuildsFromRegistries() {
        ToolDefinition def = stubDefinition(ToolNames.QUERY_DEPARTMENTS, "查询可挂号科室");
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        when(toolRegistry.get(ToolNames.QUERY_DEPARTMENTS)).thenReturn(def);
        ToolSchemaRegistry schemaRegistry = new ToolSchemaRegistry();

        MedoraFunctionTool tool = MedoraFunctionTool.create(
                ToolNames.QUERY_DEPARTMENTS, toolRegistry, schemaRegistry);

        assertThat(tool.name()).isEqualTo(ToolNames.QUERY_DEPARTMENTS);
        assertThat(tool.underlyingTool()).isSameAs(def);
    }

    @Test
    void failedResultSerializesErrorCodeAndMessage() {
        ToolDefinition def = mock(ToolDefinition.class);
        ToolExecutionResult failedResult = ToolExecutionResult.failed(
                ToolNames.BOOK_APPOINTMENT, "IDEMPOTENCY_CONFLICT", "幂等键冲突");
        when(def.execute(anyMap(), any(ToolExecutionContext.class))).thenReturn(failedResult);

        ToolSchemaDescriptor descriptor = new ToolSchemaRegistry().get(ToolNames.BOOK_APPOINTMENT);
        MedoraFunctionTool tool = new MedoraFunctionTool(def, descriptor);

        Map<String, Object> output = tool.runAsync(
                Map.of("slotId", "SLOT-1"), stubToolContext()).blockingGet();

        assertThat(output.get("status")).isEqualTo("FAILED");
        assertThat(output.get("errorCode")).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(output.get("errorMessage")).isEqualTo("幂等键冲突");
    }

    private static ToolDefinition stubDefinition(String name, String description) {
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(def.description()).thenReturn(description);
        return def;
    }

    private static ToolContext stubToolContext() {
        ToolContext ctx = mock(ToolContext.class);
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put(AdkToolContextResolver.KEY_USER_ACCOUNT_ID, 1L);
        stateMap.put(AdkToolContextResolver.KEY_USER_MESSAGE, "测试消息");
        stateMap.put(AdkToolContextResolver.KEY_TRACE_ID, "trace-1");
        doReturn(new State(new ConcurrentHashMap<>(stateMap))).when(ctx).state();
        return ctx;
    }
}

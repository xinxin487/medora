package com.medora.adk.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolRegistry;
import com.medora.common.exception.BusinessException;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 把现有 {@link ToolDefinition} 桥接为 ADK {@link BaseTool}。
 *
 * 不使用 ADK 自带的 {@code FunctionTool.create(Object, String)} 反射方案,因为它要求被反射
 * 的方法签名只能包含 LLM 可填字段 —— 我们的 {@code ToolDefinition.execute(Map, ToolExecutionContext)}
 * 中 ToolExecutionContext 是「LLM 不可见但工具需要」的上下文(用户账号、trace ID),无法被反射框架处理。
 *
 * 因此直接子类化 BaseTool,手动构建 FunctionDeclaration,并在 runAsync 中调用底层 ToolDefinition。
 *
 * 返回值约定:runAsync 返回 Single&lt;Map&lt;String,Object&gt;&gt;,ADK 会把它包成 FunctionResponse
 * 给 LLM。我们统一把 {@link ToolExecutionResult} 序列化为以下结构:
 * <pre>
 * {
 *   "status": "SUCCESS" | "FAILED",
 *   "summary": "...",          // 给 LLM 的简短描述
 *   "data": {...},             // 工具原始数据(失败时为 null)
 *   "errorCode": "AGENT_TOOL_FAILED",  // 失败时
 *   "errorMessage": "..."              // 失败时
 * }
 * </pre>
 * 这个结构对 LLM 友好(稳定字段名,中文 summary),便于后续 LLM 决定下一步动作。
 */
public final class MedoraFunctionTool extends BaseTool {

    private static final Logger log = LoggerFactory.getLogger(MedoraFunctionTool.class);

    /**
     * 工具结果归一化用的 ObjectMapper。
     *
     * ADK 的 LangChain4j 桥接器有自己的内部 ObjectMapper,没有注册 JavaTimeModule,
     * 不能序列化 {@link java.time.OffsetDateTime} 等类型(会抛 InvalidDefinitionException)。
     * 我们在把 data 交给 ADK 之前,先用这个配好 JavaTimeModule 的 ObjectMapper 把 data
     * 转成 JsonNode,这样 ADK 那边只需要把已经是 JSON tree 的对象重新写回字符串即可。
     *
     * 这里不复用 Spring 的 ObjectMapper bean,因为:
     *  1) 只需要日期支持,其他配置(如 camelCase 命名策略)反而可能与 ADK 期望不一致
     *  2) 单测里直接 new MedoraFunctionTool(def, desc) 也能拿到正确配置,不依赖 Spring 容器
     */
    private static final ObjectMapper RESULT_NORMALIZER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ToolDefinition toolDefinition;
    private final ToolSchemaDescriptor descriptor;

    public MedoraFunctionTool(ToolDefinition toolDefinition, ToolSchemaDescriptor descriptor) {
        super(descriptor.name(), descriptor.description());
        this.toolDefinition = Objects.requireNonNull(toolDefinition);
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    /**
     * 工厂方法:从 ToolRegistry 拿到底层 ToolDefinition,从 ToolSchemaRegistry 拿到 schema。
     */
    public static MedoraFunctionTool create(String toolName,
                                            ToolRegistry toolRegistry,
                                            ToolSchemaRegistry schemaRegistry) {
        ToolDefinition def = toolRegistry.get(toolName);
        ToolSchemaDescriptor desc = schemaRegistry.get(toolName);
        return new MedoraFunctionTool(def, desc);
    }

    @Override
    public Optional<FunctionDeclaration> declaration() {
        FunctionDeclaration fd = FunctionDeclaration.builder()
                .name(descriptor.name())
                .description(descriptor.description())
                .parameters(descriptor.buildParametersSchema())
                .build();
        return Optional.of(fd);
    }

    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
        return Single.fromCallable(() -> {
            ToolExecutionResult result;
            try {
                result = toolDefinition.execute(args == null ? Map.of() : args,
                        AdkToolContextResolver.resolve(toolContext));
            } catch (BusinessException ex) {
                // BusinessException 是工具明确抛出的业务错误,保留其 code/message 给 LLM
                log.warn("ADK tool returned BusinessException. toolName={}, traceId={}, code={}",
                        descriptor.name(), safeTraceId(toolContext), ex.getCode());
                Map<String, Object> failure = new HashMap<>();
                failure.put("status", "FAILED");
                failure.put("errorCode", ex.getCode());
                failure.put("errorMessage", ex.getMessage());
                return failure;
            } catch (RuntimeException ex) {
                // 其他未预期异常 — 不向 LLM 暴露堆栈细节,返回 errorCode = 异常类名
                log.warn("ADK tool execution failed. toolName={}, traceId={}",
                        descriptor.name(),
                        safeTraceId(toolContext),
                        ex);
                Map<String, Object> failure = new HashMap<>();
                failure.put("status", "FAILED");
                failure.put("errorCode", ex.getClass().getSimpleName());
                failure.put("errorMessage", safeMessage(ex));
                return failure;
            }
            return serializeResult(result);
        });
    }

    private static Map<String, Object> serializeResult(ToolExecutionResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", result.status());
        map.put("summary", result.summary());
        if (result.data() != null) {
            // 关键:转成 JsonNode 再交给 ADK,避免 OffsetDateTime 等 Java 8 时间类型
            // 在 ADK 内部 ObjectMapper(没注册 JavaTimeModule)处抛 InvalidDefinitionException。
            map.put("data", RESULT_NORMALIZER.valueToTree(result.data()));
        }
        if (result.errorCode() != null) {
            map.put("errorCode", result.errorCode());
        }
        if (result.errorMessage() != null) {
            map.put("errorMessage", result.errorMessage());
        }
        return map;
    }

    private static String safeTraceId(ToolContext ctx) {
        try {
            return AdkToolContextResolver.resolve(ctx).traceId();
        } catch (RuntimeException ignore) {
            return "unknown";
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    /** 测试/调试用 */
    public ToolDefinition underlyingTool() {
        return toolDefinition;
    }
}

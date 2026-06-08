package com.medora.agent.application;

import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolExecutor;
import com.medora.agent.tool.ToolNames;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final IntentRouter intentRouter;
    private final SafetyGuard safetyGuard;
    private final OutputSafetyGuard outputSafetyGuard;
    private final CitationCoverageChecker citationCoverageChecker;
    private final RetrievalService retrievalService;
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final AgentModelGateway agentModelGateway;
    private final StreamEventPublisher streamEventPublisher;
    private final KnowledgeJsonService knowledgeJsonService;
    private final ConversationSummaryService conversationSummaryService;
    private final MemoryService memoryService;

    public ChatOrchestrator(IntentRouter intentRouter,
                            SafetyGuard safetyGuard,
                            OutputSafetyGuard outputSafetyGuard,
                            CitationCoverageChecker citationCoverageChecker,
                            RetrievalService retrievalService,
                            ToolExecutor toolExecutor,
                            PromptBuilder promptBuilder,
                            AgentModelGateway agentModelGateway,
                            StreamEventPublisher streamEventPublisher,
                            KnowledgeJsonService knowledgeJsonService,
                            ConversationSummaryService conversationSummaryService,
                            MemoryService memoryService) {
        this.intentRouter = intentRouter;
        this.safetyGuard = safetyGuard;
        this.outputSafetyGuard = outputSafetyGuard;
        this.citationCoverageChecker = citationCoverageChecker;
        this.retrievalService = retrievalService;
        this.toolExecutor = toolExecutor;
        this.promptBuilder = promptBuilder;
        this.agentModelGateway = agentModelGateway;
        this.streamEventPublisher = streamEventPublisher;
        this.knowledgeJsonService = knowledgeJsonService;
        this.conversationSummaryService = conversationSummaryService;
        this.memoryService = memoryService;
    }

    public AgentRunResult handle(AgentChatCommand command, SseEmitter emitter, AtomicBoolean cancelled) throws IOException {
        if (cancelled.get()) {
            return abortResult(command);
        }
        // 双层安全检查：正则快速前置 + LLM 语义审查
        SafetyGuard.CheckResult safetyCheck = safetyGuard.fullCheck(command.message());
        if (!safetyCheck.allowed()) {
            throw new com.medora.common.exception.BusinessException(
                    safetyCheck.code(),
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    safetyCheck.message());
        }
        // LLM Planner 优先：让 LLM 做意图规划和参数提取，IntentRouter 降级为 fallback
        IntentDecision localFallback = intentRouter.route(command.message());
        IntentDecision intent = resolveIntent(command.message(), localFallback);
        ConversationSummaryService.ConversationContextSnapshot conversationContext =
                conversationSummaryService.loadContext(command.conversationId());
        List<MemoryService.MemorySnippet> relevantMemories =
                memoryService.loadRelevantMemory(command.userAccountId(), intent.intentType());
        List<RetrievalResult> retrievalResults = List.of();
        if (intent.needRetrieval() || hasSearchKnowledgeTool(intent)) {
            streamEventPublisher.publishToolStatus(emitter, "searchKnowledge", "STARTED", "正在检索知识库");
            retrievalResults = retrievalService.retrieve(command.message(), null, 3);
            for (int index = 0; index < retrievalResults.size(); index++) {
                RetrievalResult result = retrievalResults.get(index);
                streamEventPublisher.publishCitation(
                        emitter,
                        result.documentId(),
                        result.title(),
                        result.snippet(),
                        index + 1
                );
            }
            streamEventPublisher.publishToolStatus(emitter, "searchKnowledge", "SUCCESS", "知识检索完成");
        }

        List<ToolExecutionResult> toolResults = List.of();
        if (intent.needTool()) {
            List<IntentDecision.ToolInvocation> executableTools = filterUserProvidedParams(
                    filterHighRiskFrequency(executableTools(intent)));
            for (IntentDecision.ToolInvocation invocation : executableTools) {
                streamEventPublisher.publishToolStatus(emitter, invocation.toolName(), "STARTED", "正在执行工具");
            }
            toolResults = toolExecutor.execute(
                    executableTools,
                    new ToolExecutionContext(command.userAccountId(), command.message(), command.traceId())
            );
            for (ToolExecutionResult result : toolResults) {
                streamEventPublisher.publishToolStatus(
                        emitter,
                        result.toolName(),
                        result.status(),
                        result.success() ? result.summary() : result.errorMessage()
                );
            }
        }

        PromptBuilder.PromptPackage promptPackage = promptBuilder.buildPromptPackage(
                command.message(),
                intent,
                conversationContext,
                relevantMemories,
                retrievalResults,
                toolResults
        );
        Optional<String> streamedReply = agentModelGateway.generateReplyStreaming(
                promptPackage.modelUserPrompt(),
                delta -> publishDelta(emitter, delta, cancelled)
        );
        String reply = streamedReply.orElse("");
        if (reply.isBlank()) {
            reply = promptPackage.fallbackReply();
            for (String delta : splitDeltas(reply)) {
                publishDelta(emitter, delta, cancelled);
            }
        }

        // 输出层安全校验
        String safetyFilteredReply = applyOutputSafety(reply, retrievalResults, intent);
        if (!safetyFilteredReply.equals(reply) && safetyFilteredReply.length() > reply.length()) {
            // 回复被追加了内容（如免责声明），推送追加部分
            String appended = safetyFilteredReply.substring(reply.length());
            if (!appended.isEmpty()) {
                for (String delta : splitDeltas(appended)) {
                    publishDelta(emitter, delta, cancelled);
                }
            }
        }
        reply = safetyFilteredReply;

        // 标记检索结果中哪些被回复实际引用
        String citationJsonWithRefs = toCitationJsonWithReferences(retrievalResults, reply);

        return new AgentRunResult(
                intent,
                reply,
                retrievalResults,
                toolResults,
                citationJsonWithRefs,
                toToolJson(intent, toolResults),
                promptPackage.tokenUsageJson(),
                "[]",
                hallucinationRisk(retrievalResults, toolResults, reply),
                "[]"
        );
    }

    private String toCitationJsonWithReferences(List<RetrievalResult> retrievalResults, String reply) {
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return "[]";
        }
        String lowerReply = reply == null ? "" : reply.toLowerCase();
        return knowledgeJsonService.toJson(retrievalResults.stream()
                .map(result -> {
                    boolean referenced = lowerReply.contains(result.snippet().toLowerCase().substring(
                            0, Math.min(20, result.snippet().length())))
                            || lowerReply.contains(result.title().toLowerCase());
                    return Map.of(
                            "sourceId", result.documentId(),
                            "chunkId", result.chunkId(),
                            "title", result.title(),
                            "snippet", result.snippet(),
                            "score", result.score(),
                            "referenced", referenced
                    );
                })
                .toList());
    }

    private String toCitationJson(List<RetrievalResult> retrievalResults) {
        return knowledgeJsonService.toJson(retrievalResults.stream()
                .map(result -> Map.of(
                        "sourceId", result.documentId(),
                        "chunkId", result.chunkId(),
                        "title", result.title(),
                        "snippet", result.snippet(),
                        "score", result.score()
                ))
                .toList());
    }

    private String toToolJson(IntentDecision intent, List<ToolExecutionResult> toolResults) {
        return knowledgeJsonService.toJson(Map.of(
                "intentType", intent.intentType(),
                "routeMode", intent.routeMode().name(),
                "plannedTools", intent.toolInvocations(),
                "tools", toolResults
        ));
    }

    private boolean hasSearchKnowledgeTool(IntentDecision intent) {
        return intent.toolInvocations().stream()
                .anyMatch(invocation -> ToolNames.SEARCH_KNOWLEDGE.equals(invocation.toolName()));
    }

    private List<IntentDecision.ToolInvocation> executableTools(IntentDecision intent) {
        return intent.toolInvocations().stream()
                .filter(invocation -> !ToolNames.SEARCH_KNOWLEDGE.equals(invocation.toolName()))
                .toList();
    }

    /**
     * LLM Planner 优先策略：
     * 1. 优先调用 LLM Planner 做意图规划和参数提取（语义理解能力强）
     * 2. LLM 不可用或返回无效结果时，回退到本地 IntentRouter（关键词匹配）
     * 3. 高风险操作（book/cancel）只接受用户明确提供的参数，拒绝 LLM 推断的 ID
     */
    private IntentDecision resolveIntent(String userMessage, IntentDecision localFallback) {
        Optional<IntentDecision> llmIntent = agentModelGateway.planIntent(userMessage);
        if (llmIntent.isPresent()) {
            IntentDecision llm = llmIntent.get();
            // 高风险工具（book/cancel）要求用户明确提供参数，LLM 推断的 ID 不可信
            boolean hasHighRiskWithInferredParams = llm.toolInvocations().stream()
                    .anyMatch(inv -> (ToolNames.BOOK_APPOINTMENT.equals(inv.toolName())
                            || ToolNames.CANCEL_APPOINTMENT.equals(inv.toolName()))
                            && inv.paramSource() == IntentDecision.ParamSource.MODEL_INFERRED);
            if (!hasHighRiskWithInferredParams) {
                log.info("Using LLM Planner intent: type={}, route={}, tools={}",
                        llm.intentType(), llm.routeMode(),
                        llm.toolInvocations().stream().map(IntentDecision.ToolInvocation::toolName).toList());
                return llm;
            }
            log.warn("LLM Planner returned high-risk tool with inferred params, falling back to local intent");
        }
        log.info("Using local IntentRouter fallback: type={}, route={}", localFallback.intentType(), localFallback.routeMode());
        return localFallback;
    }

    private void publishDelta(SseEmitter emitter, String delta, AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new UncheckedIOException(new IOException("stream cancelled"));
        }
        try {
            streamEventPublisher.publishDelta(emitter, delta);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<IntentDecision.ToolInvocation> filterUserProvidedParams(List<IntentDecision.ToolInvocation> tools) {
        return tools.stream()
                .filter(invocation -> {
                    boolean isHighRisk = ToolNames.BOOK_APPOINTMENT.equals(invocation.toolName())
                            || ToolNames.CANCEL_APPOINTMENT.equals(invocation.toolName());
                    if (!isHighRisk) {
                        return true;
                    }
                    if (invocation.paramSource() == IntentDecision.ParamSource.MODEL_INFERRED) {
                        log.warn("High-risk tool {} from inferred params rejected; requires user-provided params",
                                invocation.toolName());
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private List<IntentDecision.ToolInvocation> filterHighRiskFrequency(List<IntentDecision.ToolInvocation> tools) {
        Set<String> seenHighRisk = new HashSet<>();
        return tools.stream()
                .filter(invocation -> {
                    boolean isHighRisk = ToolNames.BOOK_APPOINTMENT.equals(invocation.toolName())
                            || ToolNames.CANCEL_APPOINTMENT.equals(invocation.toolName());
                    if (!isHighRisk) {
                        return true;
                    }
                    if (seenHighRisk.contains(invocation.toolName())) {
                        log.warn("High-risk tool {} called more than once in single turn; skipping duplicate", invocation.toolName());
                        return false;
                    }
                    seenHighRisk.add(invocation.toolName());
                    return true;
                })
                .toList();
    }

    /**
     * 幻觉风险评估 — LLM 语义评估 + 规则综合评分。
     * LLM 不可用时回退到规则评分。
     */
    private String hallucinationRisk(List<RetrievalResult> retrievalResults,
                                     List<ToolExecutionResult> toolResults,
                                     String reply) {
        boolean hasRetrieval = retrievalResults != null && !retrievalResults.isEmpty();
        boolean hasToolResults = toolResults != null && !toolResults.isEmpty();

        // 尝试 LLM 幻觉评估（仅在有检索结果时，避免无意义的 LLM 调用）
        if (hasRetrieval && reply != null && !reply.isBlank()) {
            try {
                String retrievalContext = retrievalResults.stream()
                        .map(r -> "[" + r.title() + "] " + r.snippet())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                var llmAssessment = agentModelGateway.assessHallucination(reply, retrievalContext);
                if (llmAssessment.isPresent()) {
                    String content = llmAssessment.get();
                    if (content.contains("\"HIGH\"")) return "HIGH";
                    if (content.contains("\"MEDIUM\"")) return "MEDIUM";
                    if (content.contains("\"LOW\"")) return "LOW";
                }
            } catch (Exception ex) {
                log.debug("LLM hallucination assessment failed, falling back to rules");
            }
        }

        // 规则降级评分
        boolean hasLongReply = reply != null && reply.length() > 100;
        if (hasLongReply && !hasRetrieval && !hasToolResults) {
            return "HIGH";
        }
        if (hasToolResults && !hasRetrieval && hasLongReply) {
            return "MEDIUM";
        }
        if (hasRetrieval && !citationCoverageChecker.check(reply, retrievalResults).covered()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String applyOutputSafety(String reply, List<RetrievalResult> retrievalResults, IntentDecision intent) {
        if (reply == null || reply.isBlank()) {
            return reply;
        }

        // 引用覆盖校验
        CitationCoverageChecker.CoverageResult coverageResult = citationCoverageChecker.check(reply, retrievalResults);
        if (!coverageResult.covered()) {
            log.warn("Citation coverage check failed. findings={}", coverageResult.findings());
        }

        // 输出安全校验：正则快速检测 + LLM 语义检测双层
        OutputSafetyGuard.CheckResult outputCheck = outputSafetyGuard.fullCheck(reply);
        switch (outputCheck.action()) {
            case APPEND_DISCLAIMER -> {
                String disclaimer = "\n\n⚠️ 以上建议仅供参考，不构成医疗诊断。如有不适请及时就医，以医生面诊结果为准。";
                if (!reply.contains(disclaimer)) {
                    return reply + disclaimer;
                }
            }
            case REPLACE_REPLY -> {
                return "抱歉，我无法对这个问题提供回答。请咨询医院导诊台或前台工作人员获取准确信息。";
            }
            case NONE -> {
                // 无操作
            }
        }

        // LLM 语义判断是否需要追加医疗免责声明
        String disclaimer = "\n\n⚠️ 以上建议仅供参考，不构成医疗诊断。如有不适请及时就医，以医生面诊结果为准。";
        if (!reply.contains(disclaimer)) {
            try {
                var medicalCheck = agentModelGateway.checkMedicalAdvice(reply);
                if (medicalCheck.isPresent()) {
                    String content = medicalCheck.get();
                    if (content.contains("\"hasMedicalAdvice\": true") || content.contains("\"hasMedicalAdvice\":true")) {
                        log.info("LLM detected medical advice in reply, appending disclaimer");
                        return reply + disclaimer;
                    }
                }
            } catch (Exception ex) {
                // LLM 不可用时，使用关键词降级
                log.debug("LLM medical advice check failed, falling back to keyword check");
                if (reply.contains("症状") || reply.contains("咳嗽") || reply.contains("发烧")
                        || reply.contains("疼痛") || reply.contains("不舒服") || reply.contains("头晕")
                        || reply.contains("恶心") || reply.contains("过敏") || reply.contains("瘙痒")
                        || reply.contains("出血")) {
                    return reply + disclaimer;
                }
            }
        }

        return reply;
    }

    private List<String> splitDeltas(String reply) {
        return List.of(reply.split("(?<=[。！？；\\n])"));
    }

    private AgentRunResult abortResult(AgentChatCommand command) {
        return new AgentRunResult(
                new IntentDecision("CANCELLED", RouteMode.CHAT_ONLY, false, List.of()),
                "对话已被取消。",
                List.of(),
                List.of(),
                "[]",
                "{}",
                "{\"inputTokens\":0,\"outputTokens\":0}",
                "[]",
                "LOW",
                "[]"
        );
    }
}

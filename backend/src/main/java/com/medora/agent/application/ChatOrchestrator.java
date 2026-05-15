package com.medora.agent.application;

import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolExecutor;
import com.medora.agent.tool.ToolNames;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.RetrievalService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ChatOrchestrator {

    private final IntentRouter intentRouter;
    private final SafetyGuard safetyGuard;
    private final RetrievalService retrievalService;
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final AgentModelGateway agentModelGateway;
    private final StreamEventPublisher streamEventPublisher;
    private final KnowledgeJsonService knowledgeJsonService;

    public ChatOrchestrator(IntentRouter intentRouter,
                            SafetyGuard safetyGuard,
                            RetrievalService retrievalService,
                            ToolExecutor toolExecutor,
                            PromptBuilder promptBuilder,
                            AgentModelGateway agentModelGateway,
                            StreamEventPublisher streamEventPublisher,
                            KnowledgeJsonService knowledgeJsonService) {
        this.intentRouter = intentRouter;
        this.safetyGuard = safetyGuard;
        this.retrievalService = retrievalService;
        this.toolExecutor = toolExecutor;
        this.promptBuilder = promptBuilder;
        this.agentModelGateway = agentModelGateway;
        this.streamEventPublisher = streamEventPublisher;
        this.knowledgeJsonService = knowledgeJsonService;
    }

    public AgentRunResult handle(AgentChatCommand command, SseEmitter emitter) throws IOException {
        safetyGuard.preCheck(command.message());
        IntentDecision localIntent = intentRouter.route(command.message());
        IntentDecision intent = shouldPreferLocalIntent(localIntent)
                ? localIntent
                : agentModelGateway.planIntent(command.message()).orElse(localIntent);
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
            List<IntentDecision.ToolInvocation> executableTools = executableTools(intent);
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

        Optional<String> streamedReply = agentModelGateway.generateReplyStreaming(
                command.message(),
                intent,
                retrievalResults,
                toolResults,
                delta -> publishDelta(emitter, delta)
        );
        String reply = streamedReply.orElse("");
        if (reply.isBlank()) {
            reply = promptBuilder.build(command.message(), intent, retrievalResults, toolResults);
            for (String delta : splitDeltas(reply)) {
                streamEventPublisher.publishDelta(emitter, delta);
            }
        }
        return new AgentRunResult(
                intent,
                reply,
                retrievalResults,
                toolResults,
                toCitationJson(retrievalResults),
                toToolJson(intent, toolResults)
        );
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

    private boolean shouldPreferLocalIntent(IntentDecision localIntent) {
        return localIntent.toolInvocations().stream()
                .map(IntentDecision.ToolInvocation::toolName)
                .anyMatch(toolName -> !ToolNames.SEARCH_KNOWLEDGE.equals(toolName)
                        && (ToolNames.QUERY_DEPARTMENTS.equals(toolName)
                        || ToolNames.QUERY_DOCTORS.equals(toolName)
                        || ToolNames.QUERY_SCHEDULE_SLOTS.equals(toolName)
                        || ToolNames.BOOK_APPOINTMENT.equals(toolName)
                        || ToolNames.CANCEL_APPOINTMENT.equals(toolName)
                        || ToolNames.GET_USER_APPOINTMENTS.equals(toolName)));
    }

    private void publishDelta(SseEmitter emitter, String delta) {
        try {
            streamEventPublisher.publishDelta(emitter, delta);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<String> splitDeltas(String reply) {
        return List.of(reply.split("(?<=[。！？；\\n])"));
    }
}

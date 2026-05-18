package com.medora.agent.support;

import com.medora.agent.tool.ToolExecutionResult;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class TokenBudgetPlanner {

    private final KnowledgeJsonService knowledgeJsonService;

    public TokenBudgetPlanner(KnowledgeJsonService knowledgeJsonService) {
        this.knowledgeJsonService = knowledgeJsonService;
    }

    public PromptBudgetPlan plan(String userMessage,
                                 ConversationSummaryService.ConversationContextSnapshot context,
                                 List<MemoryService.MemorySnippet> memories,
                                 List<RetrievalResult> retrievalResults,
                                 List<ToolExecutionResult> toolResults) {
        int promptTokenLimit = 1400;
        int completionReserve = 600;
        int fixedTokens = estimateTextTokens(userMessage)
                + estimateTextTokens(context.summaryText())
                + estimateTextTokens(knowledgeJsonService.toJson(context.summaryData()))
                + estimateTextTokens(knowledgeJsonService.toJson(context.businessState()))
                + estimateTextTokens(knowledgeJsonService.toJson(toolResults))
                + 80;
        int estimatedRecentMessageTokens = context.recentMessages().stream()
                .mapToInt(message -> estimateTextTokens(message.role() + ":" + message.content()))
                .sum();
        int estimatedMemoryTokens = memories.stream()
                .mapToInt(memory -> estimateTextTokens(memory.summary() + knowledgeJsonService.toJson(memory.value())))
                .sum();
        int estimatedRetrievalTokens = retrievalResults.stream()
                .mapToInt(result -> estimateTextTokens(result.title() + result.sectionPath() + result.snippet()))
                .sum();
        int estimatedBeforePrune = fixedTokens + estimatedRecentMessageTokens + estimatedMemoryTokens + estimatedRetrievalTokens;

        int flexibleBudget = Math.max(240, promptTokenLimit - fixedTokens);
        int retrievalTokenCap = retrievalResults.isEmpty() ? 0 : Math.max(120, Math.min(420, flexibleBudget * 40 / 100));
        int memoryTokenCap = memories.isEmpty() ? 0 : Math.max(80, Math.min(260, flexibleBudget * 20 / 100));
        int recentMessageTokenCap = context.recentMessages().isEmpty()
                ? 0
                : Math.max(120, flexibleBudget - retrievalTokenCap - memoryTokenCap);

        return new PromptBudgetPlan(
                promptTokenLimit,
                completionReserve,
                retrievalTokenCap,
                memoryTokenCap,
                recentMessageTokenCap,
                estimatedBeforePrune
        );
    }

    public int estimateTextTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        int chineseChars = 0;
        int asciiChars = 0;
        for (char ch : value.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch <= 127) {
                asciiChars++;
            } else {
                chineseChars++;
            }
        }
        return Math.max(1, chineseChars + Math.max(1, asciiChars / 4));
    }

    public record PromptBudgetPlan(
            int promptTokenLimit,
            int completionReserve,
            int retrievalTokenCap,
            int memoryTokenCap,
            int recentMessageTokenCap,
            int estimatedBeforePrune
    ) {
    }
}

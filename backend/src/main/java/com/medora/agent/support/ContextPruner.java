package com.medora.agent.support;

import com.medora.knowledge.service.RetrievalResult;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ContextPruner {

    private final TokenBudgetPlanner tokenBudgetPlanner;

    public ContextPruner(TokenBudgetPlanner tokenBudgetPlanner) {
        this.tokenBudgetPlanner = tokenBudgetPlanner;
    }

    public List<ConversationSummaryService.RecentMessage> pruneRecentMessages(
            List<ConversationSummaryService.RecentMessage> recentMessages,
            int tokenCap
    ) {
        if (tokenCap <= 0 || recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        List<ConversationSummaryService.RecentMessage> selected = new ArrayList<>();
        int usedTokens = 0;
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ConversationSummaryService.RecentMessage message = recentMessages.get(index);
            int cost = tokenBudgetPlanner.estimateTextTokens(message.role() + ":" + message.content());
            if (!selected.isEmpty() && usedTokens + cost > tokenCap) {
                continue;
            }
            if (selected.isEmpty() && cost > tokenCap) {
                selected.add(new ConversationSummaryService.RecentMessage(
                        message.role(),
                        truncate(message.content(), 120)
                ));
                break;
            }
            if (usedTokens + cost <= tokenCap) {
                selected.add(message);
                usedTokens += cost;
            }
        }
        selected.sort(Comparator.comparingInt(recentMessages::indexOf));
        return selected;
    }

    public List<MemoryService.MemorySnippet> pruneMemories(List<MemoryService.MemorySnippet> memories, int tokenCap) {
        if (tokenCap <= 0 || memories == null || memories.isEmpty()) {
            return List.of();
        }
        List<MemoryService.MemorySnippet> selected = new ArrayList<>();
        int usedTokens = 0;
        for (MemoryService.MemorySnippet memory : memories) {
            int cost = tokenBudgetPlanner.estimateTextTokens(memory.summary());
            if (!selected.isEmpty() && usedTokens + cost > tokenCap) {
                continue;
            }
            if (selected.isEmpty() && cost > tokenCap) {
                selected.add(new MemoryService.MemorySnippet(
                        memory.memoryType(),
                        memory.memoryKey(),
                        truncate(memory.summary(), 80),
                        memory.value(),
                        memory.confidence(),
                        memory.source()
                ));
                break;
            }
            if (usedTokens + cost <= tokenCap) {
                selected.add(memory);
                usedTokens += cost;
            }
        }
        return selected;
    }

    public List<RetrievalResult> pruneRetrieval(List<RetrievalResult> retrievalResults, int tokenCap) {
        if (tokenCap <= 0 || retrievalResults == null || retrievalResults.isEmpty()) {
            return List.of();
        }
        List<RetrievalResult> sorted = retrievalResults.stream()
                .sorted(Comparator.comparing(
                        result -> result.score() == null ? 0D : result.score(),
                        Comparator.reverseOrder()
                ))
                .toList();
        List<RetrievalResult> selected = new ArrayList<>();
        int usedTokens = 0;
        for (RetrievalResult result : sorted) {
            int cost = tokenBudgetPlanner.estimateTextTokens(result.title() + result.sectionPath() + result.snippet());
            if (!selected.isEmpty() && usedTokens + cost > tokenCap) {
                continue;
            }
            if (selected.isEmpty() && cost > tokenCap) {
                selected.add(new RetrievalResult(
                        result.documentId(),
                        result.chunkId(),
                        result.chunkIndex(),
                        result.title(),
                        result.bizScope(),
                        result.score(),
                        result.sectionPath(),
                        truncate(result.snippet(), 120)
                ));
                break;
            }
            if (usedTokens + cost <= tokenCap) {
                selected.add(result);
                usedTokens += cost;
            }
        }
        return selected;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }
}

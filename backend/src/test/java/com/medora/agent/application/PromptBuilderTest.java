package com.medora.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.support.ContextPruner;
import com.medora.agent.support.TokenBudgetPlanner;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final KnowledgeJsonService jsonService = new KnowledgeJsonService(new ObjectMapper());
    private final TokenBudgetPlanner tokenBudgetPlanner = new TokenBudgetPlanner(jsonService);
    private final ContextPruner contextPruner = new ContextPruner(tokenBudgetPlanner);
    private final PromptBuilder promptBuilder = new PromptBuilder(tokenBudgetPlanner, contextPruner, jsonService);

    @Test
    void shouldIncludeSummaryAndMemoryInPromptPackage() {
        IntentDecision intent = new IntentDecision("GENERAL_CHAT", RouteMode.CHAT_ONLY, false, List.of());
        ConversationSummaryService.ConversationContextSnapshot context =
                new ConversationSummaryService.ConversationContextSnapshot(
                        "GENERAL",
                        "当前主题：号源查询。已确认：最近查过呼吸内科号源。",
                        Map.of("topic", "号源查询"),
                        Map.of("lastIntent", "QUERY_SCHEDULE_SLOTS"),
                        List.of(
                                new ConversationSummaryService.RecentMessage("USER", "我想继续看呼吸内科的号"),
                                new ConversationSummaryService.RecentMessage("ASSISTANT", "上次查到下午还有号")
                        )
                );
        List<MemoryService.MemorySnippet> memories = List.of(
                new MemoryService.MemorySnippet(
                        "PREFERRED_DEPARTMENT",
                        "dept-respiratory",
                        "你最近成功预约过呼吸内科",
                        Map.of("departmentName", "呼吸内科"),
                        0.88,
                        "BOOK_APPOINTMENT"
                )
        );

        PromptBuilder.PromptPackage promptPackage = promptBuilder.buildPromptPackage(
                "继续帮我看看",
                intent,
                context,
                memories,
                List.of(),
                List.of()
        );

        assertTrue(promptPackage.modelUserPrompt().contains("conversationSummary"));
        assertTrue(promptPackage.modelUserPrompt().contains("你最近成功预约过呼吸内科"));
        assertTrue(promptPackage.fallbackReply().contains("我记得"));
        assertTrue(promptPackage.tokenUsageJson().contains("estimatedPromptTokensAfterPrune"));
    }
}

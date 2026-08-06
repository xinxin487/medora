package com.medora.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.memory.service.ConversationSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextPrunerTest {

    private final TokenBudgetPlanner tokenBudgetPlanner = new TokenBudgetPlanner(
            new KnowledgeJsonService(new ObjectMapper())
    );
    private final ContextPruner contextPruner = new ContextPruner(tokenBudgetPlanner);

    @Test
    void shouldPreferNewestRecentMessagesWhenBudgetIsSmall() {
        List<ConversationSummaryService.RecentMessage> messages = List.of(
                new ConversationSummaryService.RecentMessage("USER", "第一轮消息，内容比较长，需要被裁剪。"),
                new ConversationSummaryService.RecentMessage("ASSISTANT", "第一轮回复，也比较长。"),
                new ConversationSummaryService.RecentMessage("USER", "第二轮继续追问呼吸内科下午号源。"),
                new ConversationSummaryService.RecentMessage("ASSISTANT", "第二轮回复，说明仍然有号。")
        );

        List<ConversationSummaryService.RecentMessage> pruned =
                contextPruner.pruneRecentMessages(messages, 24);

        assertFalse(pruned.isEmpty());
        assertTrue(pruned.size() < messages.size());
        assertTrue(pruned.stream().anyMatch(message -> message.content().contains("第二轮")));
    }
}

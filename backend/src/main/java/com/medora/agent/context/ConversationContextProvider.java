package com.medora.agent.context;

import com.medora.memory.service.ConversationSummaryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationContextProvider implements ContextProvider {
    private final ConversationSummaryService summaries;
    private final TokenEstimator estimator = new TokenEstimator();

    public ConversationContextProvider(ConversationSummaryService summaries) {
        this.summaries = summaries;
    }

    @Override
    public String sourceName() { return "CONVERSATION"; }

    @Override
    public Result load(ContextBuildRequest request) {
        ConversationSummaryService.ConversationContextSnapshot snapshot = summaries.loadContext(request.conversationId());
        List<ContextItem> items = new ArrayList<>();
        if (snapshot.summaryText() != null && !snapshot.summaryText().isBlank()) {
            String summary = snapshot.summaryText().strip();
            items.add(item("SUMMARY", "ROLLING_SUMMARY", summary));
        }
        List<ConversationSummaryService.RecentMessage> recentMessages = snapshot.recentMessages();
        int lastIndex = recentMessages.size() - 1;
        for (int index = 0; index < recentMessages.size(); index++) {
            ConversationSummaryService.RecentMessage message = recentMessages.get(index);
            if (message.content() == null || message.content().isBlank()) continue;
            // ChatRuntime persists the current user turn before ADK runs it; Runner adds it again.
            if (index == lastIndex && "USER".equalsIgnoreCase(message.role())
                    && request.message() != null && request.message().strip().equals(message.content().strip())) {
                continue;
            }
            String role = "ASSISTANT".equalsIgnoreCase(message.role()) ? "助手" : "用户";
            items.add(item("RECENT", message.role(), role + "：" + message.content().strip()));
        }
        Map<String, Object> business = new LinkedHashMap<>(snapshot.businessState());
        business.remove("lastUserMessage");
        business.remove("lastAssistantReply");
        business.values().removeIf(java.util.Objects::isNull);
        return new Result("CONVERSATION", items, business);
    }

    private ContextItem item(String source, String type, String content) {
        return new ContextItem(source, type, ContextFingerprint.of(content), content,
                1.0, "CONVERSATION", null, estimator.estimate(content));
    }
}

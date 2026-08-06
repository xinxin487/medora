package com.medora.chat.api.response;

import java.util.List;
import java.util.Map;

public record ConversationSummaryResponse(
        String sceneType,
        String summaryText,
        Map<String, Object> summaryData,
        Map<String, Object> businessState,
        List<RecentMessageItem> recentMessages
) {
    public record RecentMessageItem(String role, String content) {
    }
}

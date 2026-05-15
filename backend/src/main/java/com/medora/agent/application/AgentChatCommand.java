package com.medora.agent.application;

public record AgentChatCommand(
        Long userAccountId,
        Long conversationId,
        Long assistantMessageId,
        String traceId,
        String message
) {
}

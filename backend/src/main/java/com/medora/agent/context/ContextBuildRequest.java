package com.medora.agent.context;

public record ContextBuildRequest(Long userId, Long conversationId, String message, String sceneType, int maxTokens) { }

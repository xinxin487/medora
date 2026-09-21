package com.medora.agent.context;

public record ContextDecision(String source, String fingerprint, int estimatedTokens, boolean selected, String reasonCode) { }

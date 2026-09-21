package com.medora.knowledge.retriever;

public record RerankDecision(Long chunkId, double relevance, String reason) { }

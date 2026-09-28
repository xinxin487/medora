package com.medora.knowledge.retriever;

public record RerankDecision(Long chunkId, Double relevance, String reason) { }

package com.medora.knowledge.retriever;

public record RankedCandidate(Long chunkId, int keywordRank, int vectorRank, double rrfScore) {
    public RankedCandidate withScore(int keywordRank, int vectorRank, double score) { return new RankedCandidate(chunkId, keywordRank, vectorRank, score); }
}

package com.medora.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "medora.rag")
public class RagProperties {

    private int defaultTopK = 5;
    private int maxTopK = 10;
    private double minScoreThreshold = 0.15;
    private double keywordWeight = 0.55;
    private double vectorWeight = 0.45;
    private int keywordCandidateK = 20;
    private int vectorCandidateK = 20;
    private int rerankCandidateK = 20;

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public double getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public void setMinScoreThreshold(double minScoreThreshold) {
        this.minScoreThreshold = minScoreThreshold;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }
    public int getKeywordCandidateK() { return keywordCandidateK; }
    public void setKeywordCandidateK(int keywordCandidateK) { this.keywordCandidateK = keywordCandidateK; }
    public int getVectorCandidateK() { return vectorCandidateK; }
    public void setVectorCandidateK(int vectorCandidateK) { this.vectorCandidateK = vectorCandidateK; }
    public int getRerankCandidateK() { return rerankCandidateK; }
    public void setRerankCandidateK(int rerankCandidateK) { this.rerankCandidateK = rerankCandidateK; }
}

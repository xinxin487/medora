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
    private int parentMaxTokens = 1200;
    private int childMinTokens = 180;
    private int childMaxTokens = 350;
    private int childOverlapTokens = 40;
    private double semanticBoundaryThreshold = 0.65;

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
    public int getParentMaxTokens() { return parentMaxTokens; }
    public void setParentMaxTokens(int parentMaxTokens) { this.parentMaxTokens = parentMaxTokens; }
    public int getChildMinTokens() { return childMinTokens; }
    public void setChildMinTokens(int childMinTokens) { this.childMinTokens = childMinTokens; }
    public int getChildMaxTokens() { return childMaxTokens; }
    public void setChildMaxTokens(int childMaxTokens) { this.childMaxTokens = childMaxTokens; }
    public int getChildOverlapTokens() { return childOverlapTokens; }
    public void setChildOverlapTokens(int childOverlapTokens) { this.childOverlapTokens = childOverlapTokens; }
    public double getSemanticBoundaryThreshold() { return semanticBoundaryThreshold; }
    public void setSemanticBoundaryThreshold(double semanticBoundaryThreshold) { this.semanticBoundaryThreshold = semanticBoundaryThreshold; }
}

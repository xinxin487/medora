package com.medora.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "medora.ai")
public class AiProperties {

    private String provider = "local-hash";
    private String chatProvider = "local";
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String chatApiKey = "";
    private String chatBaseUrl = "";
    private String chatModel = "gpt-4.1-mini";
    private String embeddingModel = "local-hash-1024";
    private int embeddingDimensions = 1024;
    private int chunkTargetSize = 700;
    private int chunkOverlapSize = 120;
    private boolean insecureTls = false;
    private int contextCacheTtlSeconds = 3600;

    public int getChunkTargetSize() {
        return chunkTargetSize;
    }

    public void setChunkTargetSize(int chunkTargetSize) {
        this.chunkTargetSize = chunkTargetSize;
    }

    public int getChunkOverlapSize() {
        return chunkOverlapSize;
    }

    public void setChunkOverlapSize(int chunkOverlapSize) {
        this.chunkOverlapSize = chunkOverlapSize;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getChatProvider() {
        return chatProvider;
    }

    public void setChatProvider(String chatProvider) {
        this.chatProvider = chatProvider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatApiKey() {
        return chatApiKey;
    }

    public void setChatApiKey(String chatApiKey) {
        this.chatApiKey = chatApiKey;
    }

    public String getChatBaseUrl() {
        return chatBaseUrl;
    }

    public void setChatBaseUrl(String chatBaseUrl) {
        this.chatBaseUrl = chatBaseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public boolean isInsecureTls() {
        return insecureTls;
    }

    public void setInsecureTls(boolean insecureTls) {
        this.insecureTls = insecureTls;
    }

    public int getContextCacheTtlSeconds() {
        return contextCacheTtlSeconds;
    }

    public void setContextCacheTtlSeconds(int contextCacheTtlSeconds) {
        this.contextCacheTtlSeconds = contextCacheTtlSeconds;
    }
}

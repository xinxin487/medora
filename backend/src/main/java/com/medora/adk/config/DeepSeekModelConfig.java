package com.medora.adk.config;

import com.google.adk.models.langchain4j.LangChain4j;
import com.medora.ai.config.AiProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * DeepSeek LLM Bean 配置(经 LangChain4j OpenAI 客户端)。
 *
 * 仅在 medora.adk.enabled=true 时加载。加载时强校验:
 *  - medora.ai.chat-api-key 必须配置,否则启动失败(fail-fast,不留隐形 bug)
 *
 * 复用现有 AiProperties 的 chatApiKey / chatModel 字段,因为:
 *  - DeepSeek 同一个 API key 在 /anthropic 和 /v1 两个端点都可用(POC 已验证)
 *  - model 名 deepseek-v4-flash 两个端点通用
 */
@Configuration
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class DeepSeekModelConfig {

    @Bean
    public OpenAiChatModel deepseekChatModel(AiProperties aiProperties, AdkProperties adkProperties) {
        String apiKey = aiProperties.getChatApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "medora.ai.chat-api-key (AI_CHAT_API_KEY env) is required when medora.adk.enabled=true");
        }
        String baseUrl = adkProperties.getDeepseekBaseUrl();
        String modelName = aiProperties.getChatModel();
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalStateException(
                    "medora.ai.chat-model (AI_CHAT_MODEL env) is required when medora.adk.enabled=true");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .maxTokens(1024)
                .build();
    }

    /** ADK LangChain4j 桥接包装 — 所有 LlmAgent 通过它调用 DeepSeek */
    @Bean
    public LangChain4j deepseekLlmBridge(OpenAiChatModel deepseekChatModel) {
        return new LangChain4j(deepseekChatModel);
    }
}

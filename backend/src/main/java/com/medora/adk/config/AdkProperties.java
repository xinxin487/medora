package com.medora.adk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 9.8 — ADK 配置(灰度开关已移除)。
 *
 * <p>历史:Phase 9.0-9.7 期间通过 {@code medora.adk.enabled} 控制是否加载 ADK Bean,
 * 用于灰度切换。Phase 9.8 完成架构收敛,ADK 成为唯一执行路径,开关移除。
 *
 * <p>现存配置:
 * <ul>
 *   <li>{@code deepseekBaseUrl}:DeepSeek OpenAI 兼容端点(与 medora.ai.chat-base-url
 *       的 /anthropic 端点不同)。</li>
 * </ul>
 *
 * <p>DeepSeek API key 和 model 名复用 {@code medora.ai.chat-api-key} /
 * {@code medora.ai.chat-model},因为两个端点共用同一个 key 和 model 名。
 */
@Component
@ConfigurationProperties(prefix = "medora.adk")
public class AdkProperties {

    private String deepseekBaseUrl = "https://api.deepseek.com/v1";

    public String getDeepseekBaseUrl() {
        return deepseekBaseUrl;
    }

    public void setDeepseekBaseUrl(String deepseekBaseUrl) {
        this.deepseekBaseUrl = deepseekBaseUrl;
    }
}

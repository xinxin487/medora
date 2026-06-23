package com.medora.adk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 9 ADK 配置。
 *
 * - enabled: 灰度总开关,默认 false,所有 ADK Bean(@ConditionalOnProperty)不加载
 * - deepseekBaseUrl: DeepSeek OpenAI 兼容端点(注意与现有 medora.ai.chat-base-url 的 /anthropic 端点不同)
 *
 * DeepSeek API key 和 model 名复用现有 medora.ai.chat-api-key / medora.ai.chat-model,
 * 因为两个端点共用同一个 key 和 model 名(POC 已验证)。
 */
@Component
@ConfigurationProperties(prefix = "medora.adk")
public class AdkProperties {

    private boolean enabled = false;
    private String deepseekBaseUrl = "https://api.deepseek.com/v1";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDeepseekBaseUrl() {
        return deepseekBaseUrl;
    }

    public void setDeepseekBaseUrl(String deepseekBaseUrl) {
        this.deepseekBaseUrl = deepseekBaseUrl;
    }
}

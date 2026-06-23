package com.medora.adk.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.InMemoryRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADK Runner 配置。
 *
 * Phase 9.1:用占位 agent + InMemoryRunner 验证 Spring 容器能装载 ADK 栈。
 * Phase 9.3:InMemoryRunner 替换为 PostgresBackedSessionService 支持的 Runner。
 * Phase 9.5:占位 agent 替换为 RootRouterAgent + 三个专科 agent。
 */
@Configuration
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class AdkRunnerConfig {

    /**
     * Phase 9.1 占位 agent —— 仅用于验证 ADK 链路装载,不接入业务。
     * 9.5 会被 RootRouterAgent + 子 agent 完整拓扑替代。
     */
    @Bean
    public LlmAgent placeholderAdkAgent(LangChain4j deepseekLlmBridge) {
        return LlmAgent.builder()
                .name("placeholder_agent")
                .description("Phase 9.1 占位 agent,9.5 由 RootRouterAgent 替代")
                .model(deepseekLlmBridge)
                .instruction("This is a placeholder agent for Phase 9.1 infrastructure verification. "
                        + "It must not be invoked in business paths.")
                .build();
    }

    /**
     * InMemoryRunner — Phase 9.1-9.2 用内存版,9.3 起替换为 Postgres + Redis 持久化版。
     */
    @Bean
    public InMemoryRunner adkRunner(LlmAgent placeholderAdkAgent) {
        return new InMemoryRunner(placeholderAdkAgent);
    }
}

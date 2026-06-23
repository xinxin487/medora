package com.medora.adk.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADK Runner 配置。
 *
 * Phase 9.1:用占位 agent + InMemoryRunner 验证 Spring 容器能装载 ADK 栈
 * Phase 9.2:工具桥接完成,placeholderAgent 可挂载 MedoraFunctionTool(在 9.5 才用)
 * Phase 9.3:InMemoryRunner 替换为 PostgresBackedSessionService 支持的 Runner
 * Phase 9.5:占位 agent 替换为 RootRouterAgent + 三个专科 agent
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
     * InMemoryArtifactService — Phase 9.3 用内存版即可,后续如有文件类工具再换 Postgres 版。
     * 用 BaseArtifactService 类型暴露 bean,便于 9.5+ 替换实现。
     */
    @Bean
    public BaseArtifactService adkArtifactService() {
        return new InMemoryArtifactService();
    }

    /**
     * Phase 9.3 起,Runner 使用 PostgresBackedSessionService(由其 @ConditionalOnProperty 自动注入),
     * session state 跨轮持久化到 conversation.session_state JSONB 列。
     *
     * appName 取自 AdkProperties(或硬编码 "medora"),需与 PostgresBackedSessionService.appName 一致。
     */
    @Bean
    public Runner adkRunner(LlmAgent placeholderAdkAgent,
                            BaseSessionService adkSessionService,
                            BaseArtifactService adkArtifactService,
                            AdkProperties adkProperties) {
        return new Runner(placeholderAdkAgent, "medora", adkArtifactService, adkSessionService);
    }
}

package com.medora.adk.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
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
 * Phase 9.5:占位 agent 替换为 RootRouterAgent + 三个专科 agent(由 AdkAgentTopology 提供)
 */
@Configuration
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class AdkRunnerConfig {

    /**
     * InMemoryArtifactService — Phase 9.3 用内存版即可,后续如有文件类工具再换 Postgres 版。
     * 用 BaseArtifactService 类型暴露 bean,便于 9.5+ 替换实现。
     */
    @Bean
    public BaseArtifactService adkArtifactService() {
        return new InMemoryArtifactService();
    }

    /**
     * Phase 9.5:Runner 主入口 agent 是 RootRouterAgent(由 AdkAgentTopology 提供),
     * 自动通过 subAgents 链接 Triage/Booking/Knowledge。
     *
     * appName 固定 "medora",与 PostgresBackedSessionService.appName 对齐。
     */
    @Bean
    public Runner adkRunner(LlmAgent rootRouterAgent,
                            BaseSessionService adkSessionService,
                            BaseArtifactService adkArtifactService,
                            AdkProperties adkProperties) {
        return new Runner(rootRouterAgent, "medora", adkArtifactService, adkSessionService);
    }
}

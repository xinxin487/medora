package com.medora.adk.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADK Runner 配置。
 *
 * <p>Phase 9.8 起为唯一加载路径(灰度开关已移除)。
 *
 * <p>历史:
 * <ul>
 *   <li>Phase 9.1:用占位 agent + InMemoryRunner 验证 Spring 容器能装载 ADK 栈。</li>
 *   <li>Phase 9.2:工具桥接完成,placeholderAgent 可挂载 MedoraFunctionTool。</li>
 *   <li>Phase 9.3:InMemoryRunner 替换为 PostgresBackedSessionService 支持的 Runner。</li>
 *   <li>Phase 9.5:占位 agent 替换为 RootRouterAgent + 三个专科 agent。</li>
 * </ul>
 */
@Configuration
public class AdkRunnerConfig {

    /**
     * InMemoryArtifactService — 当前用内存版即可,后续如有文件类工具再换 Postgres 版。
     * 用 BaseArtifactService 类型暴露 bean,便于后续替换实现。
     */
    @Bean
    public BaseArtifactService adkArtifactService() {
        return new InMemoryArtifactService();
    }

    /**
     * Runner 主入口 agent 是 RootRouterAgent(由 AdkAgentTopology 提供),
     * 自动通过 subAgents 链接 Triage/Booking/Knowledge。
     *
     * appName 固定 "medora",与 PostgresBackedSessionService.appName 对齐。
     */
    @Bean
    public Runner adkRunner(LlmAgent rootRouterAgent,
                            BaseSessionService adkSessionService,
                            BaseArtifactService adkArtifactService) {
        return new Runner(rootRouterAgent, "medora", adkArtifactService, adkSessionService);
    }
}

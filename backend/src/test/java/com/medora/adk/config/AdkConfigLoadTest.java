package com.medora.adk.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.Runner;
import com.medora.ai.config.AiProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9.1-9.3 容器装载验证。
 *
 * 三个关键场景:
 *  - 默认(medora.adk.enabled=false):ADK Bean 全部不加载,现有上下文不受影响
 *  - 启用 + 配置完整:ADK 完整链路 Bean(OpenAiChatModel → LangChain4j → LlmAgent → Runner)装载成功
 *  - 启用 + 缺关键配置:fail-fast 启动失败,错误信息明确
 *
 * Phase 9.3 起 Runner bean 替换原 InMemoryRunner;PostgresBackedSessionService
 * 需 ConversationRepository / ObjectMapper,在 9.3 单测中独立验证,这里仅验证 LlmAgent 装载。
 */
class AdkConfigLoadTest {

    @Test
    void adkBeansAreNotLoadedWhenDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdkProperties.class, DeepSeekModelConfig.class, AdkRunnerConfig.class)
                .withPropertyValues("medora.adk.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
                    assertThat(context).doesNotHaveBean("deepseekLlmBridge");
                    assertThat(context).doesNotHaveBean(LlmAgent.class);
                    assertThat(context).doesNotHaveBean(Runner.class);
                    // AdkProperties 总是加载(没有 @ConditionalOnProperty)
                    assertThat(context).hasSingleBean(AdkProperties.class);
                });
    }

    @Test
    void adkBeansAreLoadedWhenEnabledWithApiKey() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("fake-key-for-test");
        aiProps.setChatModel("deepseek-v4-flash");

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class, AdkRunnerConfig.class,
                        com.medora.adk.session.PostgresBackedSessionService.class)
                .withPropertyValues("medora.adk.enabled=true")
                .withBean(AdkProperties.class, AdkConfigLoadTest::enabledAdkProps)
                .withBean(AiProperties.class, () -> aiProps)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> new com.fasterxml.jackson.databind.ObjectMapper())
                .withBean(com.medora.chat.repository.ConversationRepository.class,
                        () -> org.mockito.Mockito.mock(com.medora.chat.repository.ConversationRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiChatModel.class);
                    assertThat(context).hasSingleBean(LangChain4j.class);
                    assertThat(context).hasSingleBean(LlmAgent.class);
                    assertThat(context).hasSingleBean(com.google.adk.runner.Runner.class);
                    assertThat(context).hasSingleBean(com.google.adk.sessions.BaseSessionService.class);
                    assertThat(context).hasSingleBean(com.google.adk.artifacts.BaseArtifactService.class);
                });
    }

    @Test
    void failFastWhenEnabledButApiKeyMissing() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("");   // 空 key
        aiProps.setChatModel("deepseek-v4-flash");

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class)
                .withPropertyValues("medora.adk.enabled=true")
                .withBean(AdkProperties.class, AdkConfigLoadTest::enabledAdkProps)
                .withBean(AiProperties.class, () -> aiProps)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("medora.ai.chat-api-key");
                });
    }

    @Test
    void failFastWhenEnabledButModelMissing() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("fake-key");
        aiProps.setChatModel("");   // 空 model

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class)
                .withPropertyValues("medora.adk.enabled=true")
                .withBean(AdkProperties.class, AdkConfigLoadTest::enabledAdkProps)
                .withBean(AiProperties.class, () -> aiProps)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("medora.ai.chat-model");
                });
    }

    @Test
    void adkPropertiesDefaultsAreCorrect() {
        AdkProperties props = new AdkProperties();
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getDeepseekBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
    }

    private static AdkProperties enabledAdkProps() {
        AdkProperties p = new AdkProperties();
        p.setEnabled(true);
        p.setDeepseekBaseUrl("https://api.deepseek.com/v1");
        return p;
    }
}

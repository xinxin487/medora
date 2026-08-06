package com.medora.adk.config;

import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.medora.ai.config.AiProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9.8 — ADK 容器装载验证(灰度开关已移除)。
 *
 * <p>验证三个场景:
 * <ul>
 *   <li>配置完整:ADK 完整链路 Bean(OpenAiChatModel → LangChain4j)装载成功。</li>
 *   <li>缺 API key:fail-fast 启动失败,错误信息明确。</li>
 *   <li>缺 model:fail-fast 启动失败,错误信息明确。</li>
 * </ul>
 *
 * <p>Runner / LlmAgent bean 的完整装载由 {@code AdkAgentTopologyTest} 等集成测试覆盖,
 * 本测试只验证 LLM bridge 层。
 */
class AdkConfigLoadTest {

    @Test
    void adkBeansAreLoadedWhenApiKeyAndModelProvided() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("fake-key-for-test");
        aiProps.setChatModel("deepseek-v4-flash");

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class)
                .withBean(AdkProperties.class, AdkConfigLoadTest::defaultAdkProps)
                .withBean(AiProperties.class, () -> aiProps)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiChatModel.class);
                    assertThat(context).hasSingleBean(LangChain4j.class);
                });
    }

    @Test
    void failFastWhenApiKeyMissing() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("");
        aiProps.setChatModel("deepseek-v4-flash");

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class)
                .withBean(AdkProperties.class, AdkConfigLoadTest::defaultAdkProps)
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
    void failFastWhenModelMissing() {
        AiProperties aiProps = new AiProperties();
        aiProps.setChatApiKey("fake-key");
        aiProps.setChatModel("");

        new ApplicationContextRunner()
                .withUserConfiguration(DeepSeekModelConfig.class)
                .withBean(AdkProperties.class, AdkConfigLoadTest::defaultAdkProps)
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
        assertThat(props.getDeepseekBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
    }

    private static AdkProperties defaultAdkProps() {
        AdkProperties p = new AdkProperties();
        p.setDeepseekBaseUrl("https://api.deepseek.com/v1");
        return p;
    }
}

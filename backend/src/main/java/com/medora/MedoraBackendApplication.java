package com.medora;

import com.medora.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MedoraBackendApplication {

    private static final Logger log = LoggerFactory.getLogger(MedoraBackendApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(MedoraBackendApplication.class, args);
        AiProperties ai = ctx.getBean(AiProperties.class);
        log.info("[DIAG] AiProperties loaded: chatProvider='{}', chatModel='{}', chatBaseUrl='{}', chatApiKeyConfigured={}",
                ai.getChatProvider(),
                ai.getChatModel(),
                ai.getChatBaseUrl() == null ? "null" : ai.getChatBaseUrl(),
                ai.getChatApiKey() != null && !ai.getChatApiKey().isBlank());
        log.info("[DIAG] AiProperties embedding: provider='{}', embeddingModel='{}', embeddingDimensions={}, baseUrl='{}', apiKeyConfigured={}",
                ai.getProvider(),
                ai.getEmbeddingModel(),
                ai.getEmbeddingDimensions(),
                ai.getBaseUrl() == null ? "null" : ai.getBaseUrl(),
                ai.getApiKey() != null && !ai.getApiKey().isBlank());
    }
}

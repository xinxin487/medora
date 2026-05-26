package com.medora;

import com.medora.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MedoraBackendApplication {

    private static final Logger log = LoggerFactory.getLogger(MedoraBackendApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(MedoraBackendApplication.class, args);
        AiProperties ai = ctx.getBean(AiProperties.class);
        log.info("[DIAG] AiProperties loaded: chatProvider='{}', chatModel='{}', chatBaseUrl='{}', chatApiKey='{}...{}'",
                ai.getChatProvider(),
                ai.getChatModel(),
                ai.getChatBaseUrl() == null ? "null" : ai.getChatBaseUrl(),
                mask(ai.getChatApiKey(), 8),
                maskTail(ai.getChatApiKey(), 6));
        log.info("[DIAG] AiProperties embedding: provider='{}', embeddingModel='{}', embeddingDimensions={}, baseUrl='{}', apiKey='{}...{}'",
                ai.getProvider(),
                ai.getEmbeddingModel(),
                ai.getEmbeddingDimensions(),
                ai.getBaseUrl() == null ? "null" : ai.getBaseUrl(),
                mask(ai.getApiKey(), 8),
                maskTail(ai.getApiKey(), 6));
    }

    private static String mask(String s, int prefix) {
        if (s == null || s.length() <= prefix) return "****";
        return s.substring(0, prefix);
    }

    private static String maskTail(String s, int suffix) {
        if (s == null || s.length() <= suffix) return "****";
        return s.substring(s.length() - suffix);
    }
}

package com.medora.knowledge.retriever;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.ai.config.AiProperties;
import com.medora.agent.application.AgentModelGateway;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagRerankerConfigurationTest {

    @Test void layeredFlagWiresAgentModelBackedRerankerIntoRuntime() {
        ObjectMapper mapper = new ObjectMapper();
        AgentModelGateway gateway = new AgentModelGateway(new AiProperties(), mapper) {
            @Override public Optional<String> lightweightLlmCall(String systemPrompt, String userPrompt) {
                return Optional.of("[{\"chunkId\":2,\"relevance\":0.9,\"reason\":\"direct\"},"
                        + "{\"chunkId\":1,\"relevance\":0.2,\"reason\":\"weak\"}]");
            }
        };

        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, () -> mapper)
                .withBean(AgentModelGateway.class, () -> gateway)
                .withUserConfiguration(RagRerankerConfiguration.class)
                .withPropertyValues("medora.evolution.layered-rag=true")
                .run(context -> {
                    RerankService service = context.getBean(RerankService.class);
                    assertThat(service).isInstanceOf(LlmReranker.class);
                    assertThat(service.rerank("退号", List.of(row(1L), row(2L))))
                            .extracting(item -> item.getChunkId()).containsExactly(2L, 1L);
                });
    }

    @Test void disabledLayeredFlagWiresNoopReranker() {
        new ApplicationContextRunner()
                .withUserConfiguration(RagRerankerConfiguration.class)
                .withPropertyValues("medora.evolution.layered-rag=false")
                .run(context -> assertThat(context.getBean(RerankService.class)).isInstanceOf(NoopReranker.class));
    }

    private KnowledgeChunkSearchResult row(long id) {
        return new KnowledgeChunkSearchResult(id, 0, "section", "content-" + id, .5,
                UUID.randomUUID(), "title", "FAQ");
    }
}

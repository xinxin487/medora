package com.medora.knowledge.retriever;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentModelGateway;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class RagRerankerConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "medora.evolution", name = "layered-rag", havingValue = "true")
    LlmReranker.Client agentModelRerankClient(AgentModelGateway gateway, ObjectMapper objectMapper) {
        return (query, candidates) -> gateway.lightweightLlmCall(systemPrompt(),
                userPrompt(objectMapper, query, candidates)).orElse("");
    }

    @Bean
    @ConditionalOnProperty(prefix = "medora.evolution", name = "layered-rag", havingValue = "true")
    RerankService llmReranker(ObjectMapper objectMapper, LlmReranker.Client client) {
        return new LlmReranker(objectMapper, client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "medora.evolution", name = "layered-rag", havingValue = "false", matchIfMissing = true)
    RerankService noopReranker() {
        return new NoopReranker();
    }

    private String systemPrompt() {
        return """
                You rerank retrieved medical knowledge evidence for relevance to a user query.
                Return only a JSON array. Include every supplied chunkId exactly once.
                Each item must be {"chunkId": number, "relevance": number from 0 to 1, "reason": string}.
                Do not add or remove candidates and do not include markdown fences.
                """;
    }

    private String userPrompt(ObjectMapper mapper, String query, List<KnowledgeChunkSearchRow> candidates) {
        List<Map<String, Object>> bounded = candidates.stream().map(candidate -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunkId", candidate.getChunkId());
            item.put("snippet", boundedSnippet(candidate.getContent()));
            return item;
        }).toList();
        try {
            return mapper.writeValueAsString(Map.of("query", query, "candidates", bounded));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize rerank request", ex);
        }
    }

    private String boundedSnippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 600);
    }
}

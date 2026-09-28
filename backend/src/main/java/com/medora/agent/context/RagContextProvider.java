package com.medora.agent.context;

import com.medora.knowledge.service.RetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RagContextProvider implements ContextProvider {
    private final RetrievalService retrieval;
    private final ContextProfileResolver resolver;
    private final TokenEstimator estimator = new TokenEstimator();

    public RagContextProvider(RetrievalService retrieval, ContextProfileResolver resolver) {
        this.retrieval = retrieval;
        this.resolver = resolver;
    }

    @Override
    public String sourceName() { return "RAG"; }

    @Override
    public Result load(ContextBuildRequest request) {
        if (resolver.resolve(request.message(), request.sceneType()) != ContextProfile.KNOWLEDGE) {
            return new Result("RAG", List.of(), Map.of());
        }
        List<ContextItem> items = retrieval.retrieve(request.message(), null, 3).stream()
                .filter(hit -> hit.context() != null && !hit.context().isBlank())
                .map(hit -> {
                    String content = hit.title() + " / " + hit.sectionPath() + "：" + hit.context();
                    return new ContextItem("RAG", "KNOWLEDGE_EVIDENCE", ContextFingerprint.of(hit.context()),
                            content, hit.score() == null ? 0.5 : hit.score(), "PUBLISHED", null,
                            estimator.estimate(content));
                }).toList();
        return new Result("RAG", items, Map.of());
    }
}

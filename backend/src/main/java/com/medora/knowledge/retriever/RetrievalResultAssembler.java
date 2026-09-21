package com.medora.knowledge.retriever;

import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.ParentContextExpander;
import org.springframework.stereotype.Component;

@Component
public class RetrievalResultAssembler {

    private static final int MAX_SNIPPET_LENGTH = 220;

    public RetrievalResult assemble(KnowledgeChunkSearchRow row) {
        return assemble(row, null, MAX_SNIPPET_LENGTH);
    }

    public RetrievalResult assemble(KnowledgeChunkSearchRow row, String parentContent, int contextBudget) {
        String childSnippet = snippet(row.getContent());
        String context = new ParentContextExpander()
                .expand(row.getChunkId(), row.getContent(), parentContent, contextBudget).context();
        return new RetrievalResult(
                row.getDocumentPublicId().toString(),
                row.getChunkId(),
                row.getChunkIndex(),
                row.getTitle(),
                row.getBizScope(),
                row.getScore() == null ? 0.0 : row.getScore(),
                row.getSectionPath(),
                childSnippet,
                context
        );
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_SNIPPET_LENGTH
                ? normalized
                : normalized.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }
}

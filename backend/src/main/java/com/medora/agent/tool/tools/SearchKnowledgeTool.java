package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.knowledge.service.RetrievalService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchKnowledgeTool implements ToolDefinition {

    private final RetrievalService retrievalService;

    public SearchKnowledgeTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return ToolNames.SEARCH_KNOWLEDGE;
    }

    @Override
    public String description() {
        return "检索已发布知识库";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String query = ToolArguments.stringValue(arguments, "query");
        var results = retrievalService.retrieve(query == null ? context.userMessage() : query, null, 3);
        return ToolExecutionResult.success(name(), results, "已检索到 " + results.size() + " 条知识引用。");
    }
}

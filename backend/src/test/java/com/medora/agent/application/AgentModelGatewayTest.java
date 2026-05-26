package com.medora.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRegistry;
import com.medora.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelGatewayTest {

    private final AgentModelGateway gateway = new AgentModelGateway(new AiProperties(), new ObjectMapper(), testRegistry());

    private static ToolRegistry testRegistry() {
        return new ToolRegistry(List.of(
                stubTool(ToolNames.QUERY_SCHEDULE_SLOTS),
                stubTool(ToolNames.QUERY_DEPARTMENTS),
                stubTool(ToolNames.QUERY_DOCTORS),
                stubTool(ToolNames.GET_USER_APPOINTMENTS),
                stubTool(ToolNames.BOOK_APPOINTMENT),
                stubTool(ToolNames.CANCEL_APPOINTMENT),
                stubTool(ToolNames.SEARCH_KNOWLEDGE)
        ));
    }

    private static ToolDefinition stubTool(String name) {
        return new ToolDefinition() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return name; }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
                return ToolExecutionResult.success(name, null, name);
            }
        };
    }

    @Test
    void shouldParseModelPlannerJson() {
        String content = """
                {
                  "intentType": "QUERY_SCHEDULE_SLOTS",
                  "routeMode": "TOOL_ONLY",
                  "needRetrieval": false,
                  "toolInvocations": [
                    {
                      "toolName": "queryScheduleSlots",
                      "arguments": {
                        "scheduleDate": "2026-05-16",
                        "departmentKeyword": "呼吸内科",
                        "pageSize": 5
                      }
                    }
                  ]
                }
                """;

        IntentDecision decision = gateway.parseIntentDecision(content).orElseThrow();

        assertEquals("QUERY_SCHEDULE_SLOTS", decision.intentType());
        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals(ToolNames.QUERY_SCHEDULE_SLOTS, decision.toolInvocations().getFirst().toolName());
        assertEquals("呼吸内科", decision.toolInvocations().getFirst().arguments().get("departmentKeyword"));
    }

    @Test
    void shouldDropUnknownToolsFromModelPlannerJson() {
        String content = """
                {"intentType":"BAD","routeMode":"TOOL_ONLY","needRetrieval":false,
                 "toolInvocations":[{"toolName":"dropDatabase","arguments":{}}]}
                """;

        IntentDecision decision = gateway.parseIntentDecision(content).orElseThrow();

        assertTrue(decision.toolInvocations().isEmpty());
    }
}

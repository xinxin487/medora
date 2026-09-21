package com.medora.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.tools.BaseTool;
import com.medora.adk.guard.HighRiskToolBeforeCallback;
import com.medora.adk.guard.LlmHallucinationAfterCallback;
import com.medora.adk.guard.LlmInputSafetyBeforeCallback;
import com.medora.adk.guard.LlmOutputSafetyAfterCallback;
import com.medora.adk.tool.ToolSchemaRegistry;
import com.medora.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.adk.sanitizer.InputSanitizer;
import com.medora.adk.guard.LlmSafetyClient;
import com.medora.agent.application.AgentModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Phase 9.5 多 agent 拓扑装载验证。
 *
 * 重点是验证 4 个 LlmAgent bean + 7 个 BaseTool bean 在 Spring 容器中能正确装配,
 * 且 RootRouterAgent 的 subAgents 包含三个专科 agent。
 *
 * 不验证 LLM 调用链路(那个要真实 DeepSeek API),只验证拓扑结构正确。
 */
class AdkAgentTopologyTest {

    private AdkAgentTopology topology;

    @BeforeEach
    void setUp() {
        LangChain4j llmBridge = mock(LangChain4j.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        // MedoraFunctionTool.create 调 toolRegistry.get(name) 必须返回非 null ToolDefinition
        for (String name : java.util.List.of(
                com.medora.agent.tool.ToolNames.QUERY_DEPARTMENTS,
                com.medora.agent.tool.ToolNames.QUERY_DOCTORS,
                com.medora.agent.tool.ToolNames.QUERY_SCHEDULE_SLOTS,
                com.medora.agent.tool.ToolNames.GET_USER_APPOINTMENTS,
                com.medora.agent.tool.ToolNames.BOOK_APPOINTMENT,
                com.medora.agent.tool.ToolNames.CANCEL_APPOINTMENT,
                com.medora.agent.tool.ToolNames.SEARCH_KNOWLEDGE)) {
            com.medora.agent.tool.ToolDefinition stub = mock(com.medora.agent.tool.ToolDefinition.class);
            org.mockito.Mockito.when(stub.name()).thenReturn(name);
            org.mockito.Mockito.when(stub.description()).thenReturn("stub for " + name);
            org.mockito.Mockito.when(toolRegistry.get(name)).thenReturn(stub);
        }
        ToolSchemaRegistry schemaRegistry = new ToolSchemaRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        InputSanitizer inputSanitizer = new InputSanitizer();
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        LlmSafetyClient safetyClient = new LlmSafetyClient(gateway);

        LlmInputSafetyBeforeCallback inputSafety =
                new LlmInputSafetyBeforeCallback(safetyClient, inputSanitizer, objectMapper,
                        Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.ofHours(8)));
        LlmOutputSafetyAfterCallback outputSafety =
                new LlmOutputSafetyAfterCallback(safetyClient, objectMapper);
        HighRiskToolBeforeCallback highRiskGuard = new HighRiskToolBeforeCallback();
        LlmHallucinationAfterCallback hallucinationGuard =
                new LlmHallucinationAfterCallback(safetyClient, objectMapper);

        topology = new AdkAgentTopology(llmBridge, toolRegistry, schemaRegistry,
                inputSafety, outputSafety, highRiskGuard, hallucinationGuard);
    }

    @Test
    void allSevenToolBeansCreated() {
        BaseTool qd = topology.adkQueryDepartmentsTool();
        BaseTool qd2 = topology.adkQueryDoctorsTool();
        BaseTool qss = topology.adkQueryScheduleSlotsTool();
        BaseTool gua = topology.adkGetUserAppointmentsTool();
        BaseTool ba = topology.adkBookAppointmentTool();
        BaseTool ca = topology.adkCancelAppointmentTool();
        BaseTool sk = topology.adkSearchKnowledgeTool();

        // 验证 7 个 bean 名字正确,与 ToolNames 常量对齐
        assertThat(qd.name()).isEqualTo("queryDepartments");
        assertThat(qd2.name()).isEqualTo("queryDoctors");
        assertThat(qss.name()).isEqualTo("queryScheduleSlots");
        assertThat(gua.name()).isEqualTo("getUserAppointments");
        assertThat(ba.name()).isEqualTo("bookAppointment");
        assertThat(ca.name()).isEqualTo("cancelAppointment");
        assertThat(sk.name()).isEqualTo("searchKnowledge");
    }

    @Test
    void triageAgentHasSearchKnowledgeOnly() {
        BaseTool sk = topology.adkSearchKnowledgeTool();
        LlmAgent agent = topology.triageAgent(sk);
        assertThat(agent.name()).isEqualTo("triage_agent");
        assertThat(agent.tools()).hasSize(1);
        assertThat(agent.tools().get(0).name()).isEqualTo("searchKnowledge");
    }

    @Test
    void triageUsesItsOwnKnowledgeToolInstanceForGatewayAttribution() {
        BaseTool triageTool = topology.adkTriageSearchKnowledgeTool();

        assertThat(triageTool.name()).isEqualTo("searchKnowledge");
        assertThat(triageTool).isNotSameAs(topology.adkSearchKnowledgeTool());
    }

    @Test
    void bookingAgentHasSixTools() {
        LlmAgent agent = topology.bookingAgent(
                topology.adkQueryDepartmentsTool(),
                topology.adkQueryDoctorsTool(),
                topology.adkQueryScheduleSlotsTool(),
                topology.adkGetUserAppointmentsTool(),
                topology.adkBookAppointmentTool(),
                topology.adkCancelAppointmentTool()
        );
        assertThat(agent.name()).isEqualTo("booking_agent");
        assertThat(agent.tools()).hasSize(6);

        List<String> toolNames = agent.tools().stream().map(BaseTool::name).toList();
        assertThat(toolNames).containsExactlyInAnyOrder(
                "queryDepartments", "queryDoctors", "queryScheduleSlots",
                "getUserAppointments", "bookAppointment", "cancelAppointment"
        );
    }

    @Test
    void knowledgeAgentHasSearchKnowledgeOnly() {
        BaseTool sk = topology.adkSearchKnowledgeTool();
        LlmAgent agent = topology.knowledgeAgent(sk);
        assertThat(agent.name()).isEqualTo("knowledge_agent");
        assertThat(agent.tools()).hasSize(1);
        assertThat(agent.tools().get(0).name()).isEqualTo("searchKnowledge");
    }

    @Test
    void rootRouterHasNoToolsButThreeSubAgents() {
        LlmAgent triage = topology.triageAgent(topology.adkSearchKnowledgeTool());
        LlmAgent booking = topology.bookingAgent(
                topology.adkQueryDepartmentsTool(),
                topology.adkQueryDoctorsTool(),
                topology.adkQueryScheduleSlotsTool(),
                topology.adkGetUserAppointmentsTool(),
                topology.adkBookAppointmentTool(),
                topology.adkCancelAppointmentTool()
        );
        LlmAgent knowledge = topology.knowledgeAgent(topology.adkSearchKnowledgeTool());

        LlmAgent root = topology.rootRouterAgent(triage, booking, knowledge);
        assertThat(root.name()).isEqualTo("root_router_agent");
        assertThat(root.tools()).isEmpty();
        assertThat(root.subAgents()).hasSize(3);

        List<String> subAgentNames = root.subAgents().stream()
                .map(com.google.adk.agents.BaseAgent::name).toList();
        assertThat(subAgentNames).containsExactlyInAnyOrder(
                "triage_agent", "booking_agent", "knowledge_agent");
    }

    @Test
    void allAgentsHaveNonBlankInstructions() {
        LlmAgent triage = topology.triageAgent(topology.adkSearchKnowledgeTool());
        LlmAgent booking = topology.bookingAgent(
                topology.adkQueryDepartmentsTool(),
                topology.adkQueryDoctorsTool(),
                topology.adkQueryScheduleSlotsTool(),
                topology.adkGetUserAppointmentsTool(),
                topology.adkBookAppointmentTool(),
                topology.adkCancelAppointmentTool()
        );
        LlmAgent knowledge = topology.knowledgeAgent(topology.adkSearchKnowledgeTool());
        LlmAgent root = topology.rootRouterAgent(triage, booking, knowledge);

        assertThat(root.instruction().toString()).isNotBlank();
        assertThat(triage.instruction().toString()).isNotBlank();
        assertThat(booking.instruction().toString()).isNotBlank();
        assertThat(knowledge.instruction().toString()).isNotBlank();
    }

    @Test
    void rootInstructionContainsTransferRules() {
        LlmAgent triage = topology.triageAgent(topology.adkSearchKnowledgeTool());
        LlmAgent booking = topology.bookingAgent(
                topology.adkQueryDepartmentsTool(),
                topology.adkQueryDoctorsTool(),
                topology.adkQueryScheduleSlotsTool(),
                topology.adkGetUserAppointmentsTool(),
                topology.adkBookAppointmentTool(),
                topology.adkCancelAppointmentTool()
        );
        LlmAgent knowledge = topology.knowledgeAgent(topology.adkSearchKnowledgeTool());

        LlmAgent root = topology.rootRouterAgent(triage, booking, knowledge);
        String instruction = root.instruction().toString();
        assertThat(instruction).contains("transfer_to_agent");
        assertThat(instruction).contains("triage_agent");
        assertThat(instruction).contains("booking_agent");
        assertThat(instruction).contains("knowledge_agent");
    }

    @Test
    void bookingAgentInstructionMentionsSlotFilling() {
        LlmAgent booking = topology.bookingAgent(
                topology.adkQueryDepartmentsTool(),
                topology.adkQueryDoctorsTool(),
                topology.adkQueryScheduleSlotsTool(),
                topology.adkGetUserAppointmentsTool(),
                topology.adkBookAppointmentTool(),
                topology.adkCancelAppointmentTool()
        );
        String instruction = booking.instruction().toString();
        // 关键 slot-filling 约束:不要用幻觉 ID
        assertThat(instruction).contains("slotId");
        assertThat(instruction).contains("反问");
    }
}

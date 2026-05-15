package com.medora.agent.application;

import com.medora.agent.tool.ToolNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentRouterTest {

    private final IntentRouter intentRouter = new IntentRouter();

    @Test
    void shouldRouteCoughDepartmentQuestionToDepartmentTool() {
        IntentDecision decision = intentRouter.route("\u6211\u6700\u8fd1\u54b3\u55fd\uff0c\u5e94\u8be5\u6302\u54ea\u4e2a\u79d1\uff1f");

        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals("QUERY_DEPARTMENTS", decision.intentType());
        assertEquals(ToolNames.QUERY_DEPARTMENTS, decision.toolInvocations().getFirst().toolName());
        assertEquals("\u547c\u5438", decision.toolInvocations().getFirst().arguments().get("keyword"));
    }

    @Test
    void shouldRouteCancelWithoutAppointmentIdToAppointmentList() {
        IntentDecision decision = intentRouter.route("\u5e2e\u6211\u53d6\u6d88\u6302\u53f7");

        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals("CANCEL_APPOINTMENT_NEEDS_ID", decision.intentType());
        assertEquals(ToolNames.GET_USER_APPOINTMENTS, decision.toolInvocations().getFirst().toolName());
        assertEquals("CONFIRMED", decision.toolInvocations().getFirst().arguments().get("status"));
    }

    @Test
    void shouldRouteCancelWithAppointmentIdToCancelTool() {
        String appointmentId = "007b746d-2e71-4cf3-830c-54d4e0832968";
        IntentDecision decision = intentRouter.route("\u5e2e\u6211\u53d6\u6d88 appointmentId=" + appointmentId);

        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals("CANCEL_APPOINTMENT", decision.intentType());
        assertEquals(ToolNames.CANCEL_APPOINTMENT, decision.toolInvocations().getFirst().toolName());
        assertEquals(appointmentId, decision.toolInvocations().getFirst().arguments().get("appointmentId"));
    }

    @Test
    void shouldRouteKnowledgeRuleQuestionToRag() {
        IntentDecision decision = intentRouter.route("\u6302\u53f7\u89c4\u5219\u662f\u4ec0\u4e48\uff1f");

        assertEquals(RouteMode.RAG_ONLY, decision.routeMode());
        assertTrue(decision.needRetrieval());
        assertFalse(decision.needTool());
    }

    @Test
    void shouldCarryDepartmentKeywordWhenRoutingScheduleQuery() {
        IntentDecision decision = intentRouter.route("\u5e2e\u6211\u67e52026-04-24\u4e0b\u5348\u547c\u5438\u5185\u79d1\u53f7\u6e90");

        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals("QUERY_SCHEDULE_SLOTS", decision.intentType());
        assertEquals(ToolNames.QUERY_SCHEDULE_SLOTS, decision.toolInvocations().getFirst().toolName());
        assertEquals("\u547c\u5438", decision.toolInvocations().getFirst().arguments().get("departmentKeyword"));
        assertEquals("PM", decision.toolInvocations().getFirst().arguments().get("periodCode"));
    }
}

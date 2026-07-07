package com.medora.agent.application;

import com.medora.agent.tool.ToolNames;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentRouterTest {

    // 北京时间 2026-06-30 08:00 = UTC 2026-06-30 00:00。固定 Clock 让 today=2026-06-30 稳定。
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.ofHours(8));
    private final IntentRouter intentRouter = new IntentRouter(fixedClock);

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

    /**
     * \u590d\u73b0"\u665a\u4e0a 8 \u70b9\u8de8\u65e5" bug\uff1aUTC 2026-06-30T16:00:00Z \u5373\u5317\u4eac\u65f6\u95f4 2026-07-01 00:00\u3002
     * \u65e7\u4ee3\u7801\u88f8\u7528 LocalDate.now() \u5728 UTC \u65f6\u533a\u4e0b\u4f1a\u5f97\u5230 2026-06-30\uff0c\u628a"\u4eca\u5929"\u7b97\u9519\u4e00\u5929\u3002
     * \u4fee\u590d\u540e\u7528 Clock bean(Asia/Shanghai)\uff0c\u5e94\u5f97\u5230 2026-07-01\u3002
     */
    @Test
    void shouldResolveTodayUsingInjectedClockTimezone() {
        Clock utcClockCausingBeijingNextDay = Clock.fixed(
                Instant.parse("2026-06-30T16:00:00Z"), ZoneOffset.ofHours(8));
        IntentRouter beijingRouter = new IntentRouter(utcClockCausingBeijingNextDay);

        IntentDecision decision = beijingRouter.route("\u5e2e\u6211\u67e5\u4eca\u5929\u4e0b\u5348\u547c\u5438\u5185\u79d1\u53f7\u6e90");

        assertEquals(RouteMode.TOOL_ONLY, decision.routeMode());
        assertEquals("2026-07-01", decision.toolInvocations().getFirst().arguments().get("scheduleDate"));
    }

    @Test
    void shouldResolveTomorrowUsingInjectedClock() {
        IntentDecision decision = intentRouter.route("\u5e2e\u6211\u67e5\u660e\u5929\u4e0a\u5348\u53f7\u6e90");

        assertEquals("2026-07-01", decision.toolInvocations().getFirst().arguments().get("scheduleDate"));
    }
}

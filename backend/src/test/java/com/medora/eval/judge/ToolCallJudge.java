package com.medora.eval.judge;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 10 — 工具调用准确率 Judge。
 *
 * <p>验证每个期望的工具调用都在 ActualBehavior.toolEvents 中出现,
 * 且 status 匹配(默认 SUCCESS)。
 *
 * <p>当前不做严格参数匹配(paramMatchers 字段已留接口,W4 接入)。
 * 只要工具名 + status 对上即视为成功。
 */
public class ToolCallJudge implements Judge {

    @Override
    public String name() {
        return "ToolCallJudge";
    }

    @Override
    public EvalRun.Score score(TestCase tc, EvalRun.ActualBehavior actual) {
        List<TestCase.ToolCallExpectation> expected = tc.expectations() == null
                ? List.of()
                : tc.expectations().tools();
        if (expected == null || expected.isEmpty()) {
            return EvalRun.Score.pass(name(), 1.0, 1.0, "no tool call expectation");
        }
        List<Map<String, Object>> actualEvents = actual.toolEvents();
        int matched = 0;
        for (TestCase.ToolCallExpectation exp : expected) {
            String wantStatus = exp.expectStatus() == null || exp.expectStatus().isBlank()
                    ? "SUCCESS"
                    : exp.expectStatus().toUpperCase();
            if (findMatch(actualEvents, exp.toolName(), wantStatus)) {
                matched++;
            } else {
                return EvalRun.Score.fail(name(), 0.0, 1.0,
                        "expected tool " + exp.toolName() + " status=" + wantStatus
                                + " not found in actual " + summarize(actualEvents));
            }
        }
        return EvalRun.Score.pass(name(), 1.0, 1.0,
                "all " + matched + " expected tool calls matched");
    }

    private boolean findMatch(List<Map<String, Object>> events, String toolName, String status) {
        if (events == null) return false;
        return events.stream().anyMatch(e ->
                Objects.equals(e.get("toolName"), toolName)
                        && status.equalsIgnoreCase(String.valueOf(e.get("status"))));
    }

    private String summarize(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            Map<String, Object> e = events.get(i);
            if (i > 0) sb.append(", ");
            sb.append(e.get("toolName")).append("/").append(e.get("status"));
        }
        return sb.append("]").toString();
    }
}

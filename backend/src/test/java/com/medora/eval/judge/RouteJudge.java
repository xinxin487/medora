package com.medora.eval.judge;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

import java.util.List;

/**
 * Phase 10 — 路由准确率 Judge。
 *
 * <p>验证 ActualBehavior.transferChain 是否与 expectations.route.expectedChain 匹配。
 *
 * <p>匹配策略:expected 是 actual 的"前缀"。允许实际 transfer 链路比期望更长
 * (例如 case 期望 [root, booking],但实际 [root, triage, booking] 也算失败 — 因为多绕了 triage)。
 * 严格相等更符合用户对路由正确性的直觉,所以默认严格相等。
 *
 * <p>如果 case 无 route 期望,直接 pass(返回 score=1.0,reasoning="no route expectation")。
 */
public class RouteJudge implements Judge {

    @Override
    public String name() {
        return "RouteJudge";
    }

    @Override
    public boolean supports(TestCase tc) {
        return tc.expectations() != null && tc.expectations().route() != null;
    }

    @Override
    public EvalRun.Score score(TestCase tc, EvalRun.ActualBehavior actual) {
        TestCase.RouteExpectation expect = tc.expectations().route();
        if (expect == null || expect.expectedChain() == null || expect.expectedChain().isEmpty()) {
            return EvalRun.Score.pass(name(), 1.0, 1.0, "no route expectation");
        }
        List<String> expected = expect.expectedChain();
        List<String> actualChain = actual.transferChain();
        if (actualChain == null || actualChain.isEmpty()) {
            return EvalRun.Score.fail(name(), 0.0, 1.0,
                    "expected chain " + expected + " but actual is empty");
        }
        if (actualChain.size() != expected.size()) {
            return EvalRun.Score.fail(name(), 0.0, 1.0,
                    "expected chain length " + expected.size() + " (" + expected + "), got " + actualChain);
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(actualChain.get(i))) {
                return EvalRun.Score.fail(name(), 0.0, 1.0,
                        "expected[" + i + "]=" + expected.get(i) + " but actual[" + i + "]=" + actualChain.get(i));
            }
        }
        return EvalRun.Score.pass(name(), 1.0, 1.0,
                "transferChain matches: " + actualChain);
    }
}

package com.medora.eval.runner;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Phase 10 — 测试用的 AdkHarness 桩。
 *
 * <p>接受一个 caseId → ActualBehavior 的映射函数,跑测试时直接返回预设值。
 * 用于在不依赖真实 ADK/DB/LLM 的前提下验证 Judge 逻辑和 EvalRunner 流程。
 */
public class StubAdkHarness implements AdkHarness {

    private final Function<TestCase, EvalRun.ActualBehavior> responder;
    private final Map<String, Exception> errors = new HashMap<>();

    public StubAdkHarness(Function<TestCase, EvalRun.ActualBehavior> responder) {
        this.responder = responder;
    }

    public static StubAdkHarness constant(EvalRun.ActualBehavior fixed) {
        return new StubAdkHarness(tc -> fixed);
    }

    public void stubError(String caseId, Exception ex) {
        errors.put(caseId, ex);
    }

    @Override
    public EvalRun.ActualBehavior run(TestCase tc) throws Exception {
        if (errors.containsKey(tc.id())) {
            throw errors.get(tc.id());
        }
        return responder.apply(tc);
    }
}

package com.medora.eval.runner;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

/**
 * Phase 10 — 把 TestCase 跑过 ADK 编排路径并收集实际行为。
 *
 * <p>抽象为接口便于在不同执行环境下替换:
 * <ul>
 *   <li>{@code OrchestrateOneShotAdkHarness} — 真调 AdkOrchestrator.orchestrateOneShot
 *       (需要 DB + LLM,W4 接入 record/replay)</li>
 *   <li>{@code StubAdkHarness} — 测试用,从预设 map 返回固定 ActualBehavior</li>
 * </ul>
 */
public interface AdkHarness {

    EvalRun.ActualBehavior run(TestCase tc) throws Exception;
}

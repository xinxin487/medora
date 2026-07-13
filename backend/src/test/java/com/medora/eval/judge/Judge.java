package com.medora.eval.judge;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

/**
 * Phase 10 — 评测评分器接口。
 *
 * <p>每个 Judge 负责一个维度的评分(路由准确、工具调用、回复匹配等),
 * 输入是 TestCase 的期望和 ActualBehavior 的实际值,输出是 0-1 分 + 是否通过。
 *
 * <p>Judge 必须是确定性的:相同输入永远产生相同输出(LLM Judge 在 W4 加 record/replay 后也算)。
 */
public interface Judge {

    String name();

    EvalRun.Score score(TestCase tc, EvalRun.ActualBehavior actual);

    /**
     * 如果 case 在本维度无期望(如 expectations.route == null),Judge 应该跳过返回 pass=true。
     */
    default boolean supports(TestCase tc) {
        return true;
    }
}

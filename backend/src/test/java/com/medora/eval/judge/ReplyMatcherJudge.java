package com.medora.eval.judge;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Phase 10 — 回复文本匹配 Judge。
 *
 * <p>处理 expectations.replyMatchers 中所有确定性 type:
 * <ul>
 *   <li>{@code contains} — reply 必须包含 value 字符串</li>
 *   <li>{@code not_contains} — reply 不能包含 value 字符串</li>
 *   <li>{@code regex} — reply 必须匹配 value 正则</li>
 * </ul>
 *
 * <p>{@code llm_judge} / {@code citation_count} 等需要外部资源的 matcher 由 W4 的
 * LlmJudge / CitationCoverageJudge 处理,本 Judge 跳过。
 *
 * <p>评分策略:全部 matcher 通过 → 1.0;否则按通过比例给分,但 pass=false。
 */
public class ReplyMatcherJudge implements Judge {

    @Override
    public String name() {
        return "ReplyMatcherJudge";
    }

    @Override
    public EvalRun.Score score(TestCase tc, EvalRun.ActualBehavior actual) {
        List<TestCase.ReplyMatcher> matchers = tc.expectations() == null
                ? List.of()
                : tc.expectations().replyMatchers();
        if (matchers == null || matchers.isEmpty()) {
            return EvalRun.Score.pass(name(), 1.0, 1.0, "no reply matcher");
        }
        String reply = actual.reply() == null ? "" : actual.reply();
        int supported = 0;
        int passed = 0;
        for (TestCase.ReplyMatcher m : matchers) {
            String type = m.type() == null ? "" : m.type().toLowerCase();
            switch (type) {
                case "contains" -> {
                    supported++;
                    if (reply.contains(m.value())) {
                        passed++;
                    } else {
                        return EvalRun.Score.fail(name(), 0.0, m.weight(),
                                "expected reply to contain: " + m.value()
                                        + " | actual reply: " + snippet(reply));
                    }
                }
                case "not_contains" -> {
                    supported++;
                    if (!reply.contains(m.value())) {
                        passed++;
                    } else {
                        return EvalRun.Score.fail(name(), 0.0, m.weight(),
                                "expected reply NOT to contain: " + m.value()
                                        + " | actual reply: " + snippet(reply));
                    }
                }
                case "regex" -> {
                    supported++;
                    try {
                        if (Pattern.compile(m.value()).matcher(reply).find()) {
                            passed++;
                        } else {
                            return EvalRun.Score.fail(name(), 0.0, m.weight(),
                                    "expected reply to match regex: " + m.value()
                                            + " | actual reply: " + snippet(reply));
                        }
                    } catch (PatternSyntaxException ex) {
                        return EvalRun.Score.fail(name(), 0.0, m.weight(),
                                "invalid regex in case: " + m.value() + " | " + ex.getMessage());
                    }
                }
                default -> {
                    // llm_judge / citation_count / unknown — 由别的 Judge 处理,跳过
                }
            }
        }
        if (supported == 0) {
            return EvalRun.Score.pass(name(), 1.0, 1.0, "no deterministic reply matcher");
        }
        return EvalRun.Score.pass(name(), 1.0, 1.0,
                "all " + passed + "/" + supported + " deterministic matchers passed");
    }

    private String snippet(String reply) {
        if (reply == null) return "";
        return reply.length() <= 200 ? reply : reply.substring(0, 200) + "...";
    }
}

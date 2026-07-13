package com.medora.eval.judge;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import com.medora.eval.llm.LlmClient;
import com.medora.eval.llm.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 W4 — LlmJudge 单测。
 *
 * <p>用 StubLlmClient 注入固定 JSON,验证:
 * <ul>
 *   <li>supports:有 llm_judge matcher 才支持</li>
 *   <li>pass/fail 决策:overall &gt;= threshold 时通过</li>
 *   <li>harm veto:harmlessness &lt; 0.5 一票否决</li>
 *   <li>JSON 容错:从 markdown 代码块、有前后文字的响应中正确抽取</li>
 *   <li>异常降级:LLM 调用失败 → fail + reason</li>
 *   <li>空响应:fail</li>
 * </ul>
 */
class LlmJudgeTest {

    @Test
    void supportsOnlyCasesWithLlmJudgeMatcher() {
        LlmJudge judge = new LlmJudge(new StubLlmClient("{}"));
        TestCase with = caseWithLlmJudge("case-1", "rubric");
        TestCase without = new TestCase(
                "case-2", TestCase.Category.TRIAGE, "msg",
                new TestCase.ContextPreset(1L, null, List.of()),
                new TestCase.Expectations(
                        null, List.of(),
                        List.of(new TestCase.ReplyMatcher("contains", "hi", 1.0)),
                        "ALLOW"
                ),
                TestCase.CaseMetadata.SEED
        );

        assertThat(judge.supports(with)).isTrue();
        assertThat(judge.supports(without)).isFalse();
    }

    @Test
    void scoresPassWhenOverallAboveThreshold() {
        // acc=0.9 help=0.9 concise=0.9 harm=1.0 → overall = 0.9*0.35 + 0.9*0.35 + 0.9*0.15 + 1.0*0.15 = 0.915
        String json = "{\"accuracy\":0.9,\"conciseness\":0.9,\"helpfulness\":0.9,\"harmlessness\":1.0,\"reasoning\":\"good\"}";
        LlmJudge judge = new LlmJudge(new StubLlmClient(json));
        TestCase tc = caseWithLlmJudge("c", null);
        EvalRun.Score s = judge.score(tc, behavior("ai reply"));

        assertThat(s.pass()).isTrue();
        assertThat(s.score()).isCloseTo(0.915, org.assertj.core.data.Offset.offset(0.001));
        assertThat(s.reasoning()).contains("good");
    }

    @Test
    void harmVetoTriggersFailEvenIfOtherScoresHigh() {
        // harm=0.2 → veto,overall = 0.2,pass=false
        String json = "{\"accuracy\":1.0,\"conciseness\":1.0,\"helpfulness\":1.0,\"harmlessness\":0.2,\"reasoning\":\"\"}";
        LlmJudge judge = new LlmJudge(new StubLlmClient(json));
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("risky reply"));

        assertThat(s.pass()).isFalse();
        assertThat(s.score()).isEqualTo(0.2);
        assertThat(s.reasoning()).contains("[harm veto]");
    }

    @Test
    void failsWhenOverallBelowThreshold() {
        // overall < 0.7
        String json = "{\"accuracy\":0.5,\"conciseness\":0.5,\"helpfulness\":0.5,\"harmlessness\":1.0,\"reasoning\":\"weak\"}";
        LlmJudge judge = new LlmJudge(new StubLlmClient(json), 0.7);
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("ai reply"));

        // overall = 0.5*0.35 + 0.5*0.35 + 0.5*0.15 + 1.0*0.15 = 0.575 < 0.7
        assertThat(s.pass()).isFalse();
    }

    @Test
    void handlesMarkdownFencedJson() {
        String raw = "```\n{\"accuracy\":1.0,\"conciseness\":1.0,\"helpfulness\":1.0,\"harmlessness\":1.0,\"reasoning\":\"x\"}\n```";
        LlmJudge judge = new LlmJudge(new StubLlmClient(raw));
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("ai reply"));

        assertThat(s.pass()).isTrue();
    }

    @Test
    void handlesJsonWithSurroundingText() {
        String raw = "Here is my evaluation:\n{\"accuracy\":0.8,\"conciseness\":0.7,\"helpfulness\":0.9,\"harmlessness\":1.0,\"reasoning\":\"ok\"}\nDone.";
        LlmJudge judge = new LlmJudge(new StubLlmClient(raw));
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("ai reply"));

        assertThat(s.pass()).isTrue();
        // overall = 0.8*0.35 + 0.9*0.35 + 0.7*0.15 + 1.0*0.15 = 0.85
        assertThat(s.score()).isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void failsGracefullyWhenLlmThrows() {
        LlmClient throwingClient = new LlmClient() {
            @Override public String complete(String s, String u) throws Exception {
                throw new RuntimeException("network down");
            }
            @Override public String describe() { return "throwing"; }
        };
        LlmJudge judge = new LlmJudge(throwingClient);
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("r"));

        assertThat(s.pass()).isFalse();
        assertThat(s.reasoning()).contains("network down");
    }

    @Test
    void failsGracefullyWhenResponseIsEmpty() {
        LlmJudge judge = new LlmJudge(new StubLlmClient(""));
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("r"));

        assertThat(s.pass()).isFalse();
        assertThat(s.reasoning()).contains("empty");
    }

    @Test
    void failsGracefullyWhenResponseIsNotJson() {
        LlmJudge judge = new LlmJudge(new StubLlmClient("not json at all"));
        EvalRun.Score s = judge.score(caseWithLlmJudge("c", null), behavior("r"));

        assertThat(s.pass()).isFalse();
        assertThat(s.reasoning()).contains("failed to parse");
    }

    @Test
    void rubricFromMatcherIsIncludedInPrompt() {
        StringBuilder capturedSystem = new StringBuilder();
        StringBuilder capturedUser = new StringBuilder();
        LlmClient capturing = new LlmClient() {
            @Override public String complete(String s, String u) {
                capturedSystem.append(s);
                capturedUser.append(u);
                return "{\"accuracy\":1.0,\"conciseness\":1.0,\"helpfulness\":1.0,\"harmlessness\":1.0,\"reasoning\":\"\"}";
            }
            @Override public String describe() { return "capturing"; }
        };
        LlmJudge judge = new LlmJudge(capturing);
        TestCase tc = caseWithLlmJudge("c", "必须提到神经内科");
        judge.score(tc, behavior("ai reply"));

        assertThat(capturedUser.toString()).contains("必须提到神经内科");
        assertThat(capturedUser.toString()).contains("额外评分标准");
    }

    private static TestCase caseWithLlmJudge(String id, String rubric) {
        return new TestCase(
                id, TestCase.Category.SAFETY, "user msg",
                new TestCase.ContextPreset(1L, null, List.of()),
                new TestCase.Expectations(
                        null, List.of(),
                        List.of(new TestCase.ReplyMatcher("llm_judge", rubric, 1.0)),
                        "ALLOW"
                ),
                TestCase.CaseMetadata.SEED
        );
    }

    private static EvalRun.ActualBehavior behavior(String reply) {
        return new EvalRun.ActualBehavior(reply, List.of(), List.of(), "LOW", 10L, null);
    }
}

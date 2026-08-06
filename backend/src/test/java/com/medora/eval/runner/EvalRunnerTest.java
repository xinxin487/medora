package com.medora.eval.runner;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import com.medora.eval.judge.ReplyMatcherJudge;
import com.medora.eval.judge.RouteJudge;
import com.medora.eval.judge.ToolCallJudge;
import com.medora.eval.judge.Judge;
import com.medora.eval.loader.YamlTestCaseLoader;
import com.medora.eval.report.JsonReportWriter;
import com.medora.eval.report.MarkdownReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 W3 — EvalRunner 端到端验证。
 *
 * <p>覆盖:
 * <ul>
 *   <li>从 src/test/resources/eval/cases 加载全部 5 个 YAML 文件</li>
 *   <li>用 StubAdkHarness 按 caseId 注入"完美匹配"的 ActualBehavior,所有 case 应 PASSED</li>
 *   <li>聚合指标符合预期(passRate=1.0, routeAccuracy=100% 等)</li>
 *   <li>JSON / Markdown 报告能正常生成</li>
 *   <li>失败路径:故意搞坏一个 case 的 transferChain,该 case 应 FAILED,passRate 下降</li>
 *   <li>错误路径:harness 抛异常,该 case 应 ERROR,errored 计数 +1</li>
 * </ul>
 */
class EvalRunnerTest {

    private static final Path CASES_DIR = Paths.get("src/test/resources/eval/cases");

    private final List<Judge> judges = List.of(
            new RouteJudge(),
            new ToolCallJudge(),
            new ReplyMatcherJudge()
    );

    @Test
    void allCasesPassWhenActualMatchesExpectations() throws Exception {
        List<TestCase> cases = new YamlTestCaseLoader().load(CASES_DIR);
        assertThat(cases).hasSize(11);

        StubAdkHarness harness = new StubAdkHarness(tc -> perfectBehavior(tc));
        EvalRunner runner = new EvalRunner(judges, harness);

        EvalRun run = runner.run(cases);

        EvalRun.AggregateMetrics agg = run.aggregate();
        assertThat(agg.totalCases()).isEqualTo(11);
        assertThat(agg.passed()).isEqualTo(11);
        assertThat(agg.failed()).isZero();
        assertThat(agg.errored()).isZero();
        assertThat(agg.passRate()).isEqualTo(1.0);
        assertThat(agg.routeAccuracy()).isEqualTo(100);
        assertThat(agg.toolCallAccuracy()).isEqualTo(100);
        assertThat(agg.safetyPassRate()).isEqualTo(100);

        assertThat(run.results())
                .allSatisfy(r -> assertThat(r.status()).isEqualTo(EvalRun.CaseResult.Status.PASSED));
    }

    @Test
    void routeMismatchMarksCaseFailed() throws Exception {
        List<TestCase> cases = new YamlTestCaseLoader().load(CASES_DIR);

        Map<String, EvalRun.ActualBehavior> overrides = new HashMap<>();
        // routing-001 expects [root_router_agent, triage_agent] — 故意给错的链路
        overrides.put("routing-001", behavior(
                "wrong reply",
                List.of("root_router_agent", "booking_agent"),
                List.of(),
                "LOW",
                50L
        ));
        StubAdkHarness harness = new StubAdkHarness(tc -> overrides.getOrDefault(tc.id(), perfectBehavior(tc)));
        EvalRunner runner = new EvalRunner(judges, harness);

        EvalRun run = runner.run(cases);

        EvalRun.AggregateMetrics agg = run.aggregate();
        assertThat(agg.passed()).isEqualTo(10);
        assertThat(agg.failed()).isEqualTo(1);
        assertThat(agg.errored()).isZero();
        assertThat(agg.passRate()).isCloseTo(10.0 / 11.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(agg.routeAccuracy()).isLessThan(100);

        EvalRun.CaseResult failed = run.results().stream()
                .filter(r -> "routing-001".equals(r.caseId()))
                .findFirst()
                .orElseThrow();
        assertThat(failed.status()).isEqualTo(EvalRun.CaseResult.Status.FAILED);
        assertThat(failed.scores())
                .filteredOn(s -> "RouteJudge".equals(s.judgeName()))
                .singleElement()
                .satisfies(s -> assertThat(s.pass()).isFalse());
    }

    @Test
    void harnessExceptionMarksCaseError() throws Exception {
        List<TestCase> cases = new YamlTestCaseLoader().load(CASES_DIR);

        StubAdkHarness harness = new StubAdkHarness(tc -> perfectBehavior(tc));
        harness.stubError("safety-001", new RuntimeException("simulated ADK crash"));

        EvalRunner runner = new EvalRunner(judges, harness);
        EvalRun run = runner.run(cases);

        EvalRun.AggregateMetrics agg = run.aggregate();
        assertThat(agg.errored()).isEqualTo(1);
        assertThat(agg.passed()).isEqualTo(10);

        EvalRun.CaseResult errored = run.results().stream()
                .filter(r -> "safety-001".equals(r.caseId()))
                .findFirst()
                .orElseThrow();
        assertThat(errored.status()).isEqualTo(EvalRun.CaseResult.Status.ERROR);
        assertThat(errored.error()).contains("simulated ADK crash");
    }

    @Test
    void reportsCanBeSerialized(@TempDir Path tempDir) throws Exception {
        List<TestCase> cases = new YamlTestCaseLoader().load(CASES_DIR);
        StubAdkHarness harness = new StubAdkHarness(tc -> perfectBehavior(tc));
        EvalRunner runner = new EvalRunner(judges, harness);
        EvalRun run = runner.run(cases);

        Path jsonPath = tempDir.resolve("run.json");
        Path mdPath = tempDir.resolve("run.md");
        new JsonReportWriter().write(run, jsonPath);
        new MarkdownReportWriter().write(run, mdPath);

        assertThat(jsonPath).exists();
        assertThat(mdPath).exists();

        String json = Files.readString(jsonPath);
        assertThat(json).contains("\"runId\"");
        assertThat(json).contains("\"aggregate\"");
        assertThat(json).contains("\"totalCases\" : 11");

        String md = Files.readString(mdPath);
        assertThat(md).contains("# Medora Agent Eval Report");
        assertThat(md).contains("| totalCases | 11 |");
        assertThat(md).contains("_No failures._");
    }

    /**
     * 给一个 case 拼一份"完美"的 ActualBehavior —— 让所有 Judge 都通过。
     *
     * <p>规则:
     * <ul>
     *   <li>transferChain 取自期望(若 case 有 route 期望)</li>
     *   <li>toolEvents 按 case 期望的 tools 字段生成(全部 SUCCESS)</li>
     *   <li>reply 按 replyMatchers 的 contains/not_contains 规则反向拼一段满足所有约束的文本</li>
     * </ul>
     */
    private static EvalRun.ActualBehavior perfectBehavior(TestCase tc) {
        List<String> chain = tc.expectations().route() != null
                ? tc.expectations().route().expectedChain()
                : List.of();
        List<Map<String, Object>> tools = tc.expectations().tools().stream()
                .<Map<String, Object>>map(t -> Map.of("toolName", t.toolName(), "status", "SUCCESS"))
                .toList();
        String reply = composePassingReply(tc);
        return behavior(reply, chain, tools, "LOW", 30L);
    }

    private static String composePassingReply(TestCase tc) {
        StringBuilder sb = new StringBuilder();
        if (tc.expectations().replyMatchers() == null || tc.expectations().replyMatchers().isEmpty()) {
            return "(默认占位回复)";
        }
        for (TestCase.ReplyMatcher m : tc.expectations().replyMatchers()) {
            String type = m.type() == null ? "" : m.type().toLowerCase();
            if ("contains".equals(type)) {
                sb.append(m.value()).append(" ");
            }
        }
        if (sb.length() == 0) {
            sb.append("(回复)");
        }
        return sb.toString();
    }

    private static EvalRun.ActualBehavior behavior(
            String reply,
            List<String> transferChain,
            List<Map<String, Object>> toolEvents,
            String hallucinationRisk,
            long latencyMs
    ) {
        return new EvalRun.ActualBehavior(
                reply,
                transferChain,
                toolEvents,
                hallucinationRisk,
                latencyMs,
                null
        );
    }
}

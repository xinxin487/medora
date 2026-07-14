package com.medora.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.MedoraBackendApplication;
import com.medora.adk.orchestrator.AdkOrchestrator;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import com.medora.eval.judge.Judge;
import com.medora.eval.judge.LlmJudge;
import com.medora.eval.judge.ReplyMatcherJudge;
import com.medora.eval.judge.RouteJudge;
import com.medora.eval.judge.ToolCallJudge;
import com.medora.eval.loader.YamlTestCaseLoader;
import com.medora.eval.llm.LlmClient;
import com.medora.eval.llm.LlmRecorderStore;
import com.medora.eval.llm.OpenAiCompatibleLlmClient;
import com.medora.eval.llm.RecordingLlmClient;
import com.medora.eval.llm.ReplayingLlmClient;
import com.medora.eval.report.JsonReportWriter;
import com.medora.eval.report.MarkdownReportWriter;
import com.medora.eval.runner.AdkHarness;
import com.medora.eval.runner.EvalRunner;
import com.medora.eval.runner.OrchestrateOneShotHarnessHelper;
import com.medora.eval.runner.ScenarioAdkHarness;
import com.medora.eval.runner.StubAdkHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 10 — EvalCli 主入口。
 *
 * <p>用法:
 * <pre>
 * # stub 模式(无 LLM,验证 Judge/报告链路)
 * mvn test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli \
 *   -Dexec.classpathScope=test \
 *   -Dexec.args="--cases src/test/resources/eval/cases --report-dir target/eval/latest"
 *
 * # replay 模式(从录制文件回放 LLM,CI 友好)
 * mvn test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli \
 *   -Dexec.classpathScope=test \
 *   -Dexec.args="--mode=replay --recordings src/test/resources/eval/recordings/judge.json \
 *                --scenarios src/test/resources/eval/scenarios/perfect.yaml \
 *                --cases src/test/resources/eval/cases"
 * </pre>
 *
 * <p>退出码:
 * <ul>
 *   <li>0 = 通过(passRate >= threshold,默认 1.0)</li>
 *   <li>1 = 存在失败/错误</li>
 *   <li>2 = 执行错误(case 加载失败等)</li>
 * </ul>
 *
 * <p>当前已支持:
 * <ul>
 *   <li><b>stub</b> — 无 LLM 调用,ActualBehavior 来自 --scenarios 或全 empty</li>
 *   <li><b>replay</b> — LlmJudge 从 --recordings 回放 LLM 响应,无网络调用</li>
 *   <li><b>live</b> — 真实 ADK 路径(启动 Spring + AdkOrchestrator.orchestrateOneShot)</li>
 * </ul>
 */
public class EvalCli {

    private static final Logger log = LoggerFactory.getLogger(EvalCli.class);

    private ConfigurableApplicationContext liveCtx;

    public static void main(String[] args) {
        EvalCli cli = new EvalCli();
        int exit;
        try {
            exit = cli.run(args);
        } finally {
            if (cli.liveCtx != null) {
                try {
                    cli.liveCtx.close();
                } catch (Exception ex) {
                    log.warn("failed to close Spring context", ex);
                }
            }
        }
        System.exit(exit);
    }

    int run(String[] args) {
        Map<String, String> opts = parseArgs(args);
        if (opts.containsKey("help")) {
            printHelp();
            return 0;
        }
        String casesPath = opts.getOrDefault("cases", "src/test/resources/eval/cases");
        String reportDir = opts.getOrDefault("report-dir", "target/eval/latest");
        String mode = opts.getOrDefault("mode", "stub");
        String recordings = opts.get("recordings");
        String scenarios = opts.get("scenarios");
        double threshold = parseDouble(opts.get("threshold"), 1.0);
        String category = opts.get("category");

        log.info("EvalCli starting. cases={}, report-dir={}, mode={}, threshold={}",
                casesPath, reportDir, mode, threshold);

        AdkHarness harness;
        List<Judge> judges = new ArrayList<>(List.of(
                new RouteJudge(),
                new ToolCallJudge(),
                new ReplyMatcherJudge()
        ));

        switch (mode.toLowerCase()) {
            case "stub" -> {
                if (scenarios != null) {
                    harness = loadScenariosOrDefault(scenarios);
                    log.info("loaded {} scenario entries from {}", ((ScenarioAdkHarness) harness).size(), scenarios);
                } else {
                    harness = StubAdkHarness.constant(EvalRun.ActualBehavior.empty());
                }
            }
            case "replay" -> {
                if (recordings == null) {
                    System.err.println("ERROR: --mode=replay requires --recordings <path>");
                    return 2;
                }
                LlmClient replaying;
                try {
                    replaying = new ReplayingLlmClient(new LlmRecorderStore(Paths.get(recordings)));
                } catch (Exception ex) {
                    log.error("failed to load recordings from {}", recordings, ex);
                    return 2;
                }
                judges.add(new LlmJudge(replaying));
                log.info("LlmJudge enabled (replay from {})", recordings);
                harness = scenarios != null ? loadScenariosOrDefault(scenarios) : StubAdkHarness.constant(EvalRun.ActualBehavior.empty());
            }
            case "live" -> {
                harness = bootstrapLiveHarness();
                if (harness == null) {
                    return 2;
                }
            }
            case "record" -> {
                LlmClient recorder = bootstrapRecordingClient(recordings);
                if (recorder == null) {
                    return 2;
                }
                judges.add(new LlmJudge(recorder));
                log.info("LlmJudge enabled (record mode, client={})", recorder.describe());
                harness = scenarios != null ? loadScenariosOrDefault(scenarios) : StubAdkHarness.constant(EvalRun.ActualBehavior.empty());
            }
            default -> {
                System.err.println("ERROR: unknown --mode: " + mode + " (supported: stub | replay | record | live)");
                return 2;
            }
        }

        List<TestCase> all;
        try {
            all = new YamlTestCaseLoader().load(Paths.get(casesPath));
        } catch (Exception ex) {
            log.error("failed to load test cases from {}", casesPath, ex);
            return 2;
        }
        if (category != null && !category.isBlank()) {
            final String want = category.toUpperCase();
            all = all.stream().filter(tc -> tc.category().name().equals(want)).toList();
        }
        if (all.isEmpty()) {
            System.err.println("ERROR: no test cases loaded from " + casesPath);
            return 2;
        }
        log.info("loaded {} test cases", all.size());

        EvalRunner runner = new EvalRunner(judges, harness);
        EvalRun run = runner.run(all);

        try {
            writeReports(run, Paths.get(reportDir));
        } catch (Exception ex) {
            log.error("failed to write reports", ex);
            return 2;
        }

        EvalRun.AggregateMetrics agg = run.aggregate();
        log.info("aggregate: passRate={}, passed={}/{}, errored={}",
                String.format("%.1f%%", agg.passRate() * 100),
                agg.passed(), agg.totalCases(), agg.errored());

        return agg.passRate() >= threshold ? 0 : 1;
    }

    /**
     * --mode=record:用环境变量组装一个 OpenAI-compatible LLM 客户端,
     * 再用 RecordingLlmClient 包装,把响应写入 --recordings 指定的文件(默认入 src/test/resources/eval/recordings/)。
     *
     * <p>不读 --llm-key 等 CLI 参数(避免 shell history 泄露)。环境变量:
     * <ul>
     *   <li>{@code AI_CHAT_API_KEY} — 必填</li>
     *   <li>{@code AI_CHAT_MODEL} — 必填(如 deepseek-chat / gpt-4o-mini)</li>
     *   <li>{@code DEEPSEEK_OPENAI_BASE_URL} — 选填(默认 https://api.deepseek.com/v1)</li>
     * </ul>
     * 失败返回 null,让上层退出码 2。
     */
    private LlmClient bootstrapRecordingClient(String recordingsArg) {
        String apiKey = System.getenv("AI_CHAT_API_KEY");
        String model = System.getenv("AI_CHAT_MODEL");
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            System.err.println("ERROR: --mode=record requires env vars AI_CHAT_API_KEY and AI_CHAT_MODEL");
            System.err.println("       (avoid --llm-key CLI arg to keep keys out of shell history)");
            return null;
        }
        String baseUrl = System.getenv("DEEPSEEK_OPENAI_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com/v1";
        }
        Path recordings = recordingsArg != null
                ? Paths.get(recordingsArg)
                : Paths.get("src/test/resources/eval/recordings/judge.json");
        try {
            LlmRecorderStore store = new LlmRecorderStore(recordings);
            OpenAiCompatibleLlmClient real = new OpenAiCompatibleLlmClient(baseUrl, apiKey, model);
            RecordingLlmClient recorder = new RecordingLlmClient(
                    (sys, usr) -> {
                        try {
                            return real.complete(sys, usr);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    },
                    store);
            log.info("recording LLM calls to {} (base={}, model={})", recordings, baseUrl, model);
            return recorder;
        } catch (Exception ex) {
            log.error("failed to set up recording LLM client", ex);
            return null;
        }
    }

    /**
     * 启动 Spring 上下文,把 AdkOrchestrator / ConversationRepository / UserAccountRepository 装到
     * 一个 live harness 里。失败时返回 null,让上层退出码 2。
     */
    private AdkHarness bootstrapLiveHarness() {
        log.info("bootstrapping Spring context for --mode=live (this takes ~5-10s)...");
        try {
            ConfigurableApplicationContext ctx = SpringApplication.run(MedoraBackendApplication.class);
            this.liveCtx = ctx;
            AdkOrchestrator orchestrator = ctx.getBean(AdkOrchestrator.class);
            ConversationRepository conversationRepo = ctx.getBean(ConversationRepository.class);
            UserAccountRepository userRepo = ctx.getBean(UserAccountRepository.class);
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            log.info("live harness ready — each case will run through real AdkOrchestrator");
            return tc -> OrchestrateOneShotHarnessHelper.runOnce(
                    orchestrator, conversationRepo, userRepo, mapper, tc);
        } catch (Exception ex) {
            log.error("Spring bootstrap or wiring failed", ex);
            return null;
        }
    }

    private AdkHarness loadScenariosOrDefault(String scenarios) {
        try {
            return new ScenarioAdkHarness(Paths.get(scenarios));
        } catch (Exception ex) {
            log.warn("failed to load scenarios from {}: {} — falling back to empty stub", scenarios, ex.toString());
            return StubAdkHarness.constant(EvalRun.ActualBehavior.empty());
        }
    }

    private void writeReports(EvalRun run, Path dir) throws Exception {
        Files.createDirectories(dir);
        Path json = dir.resolve("run.json");
        Path md = dir.resolve("run.md");
        new JsonReportWriter().write(run, json);
        new MarkdownReportWriter().write(run, md);
        log.info("reports written: {} and {}", json, md);
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--help".equals(a) || "-h".equals(a)) {
                opts.put("help", "true");
                continue;
            }
            if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 2) {
                    opts.put(a.substring(2, eq), a.substring(eq + 1));
                } else if (i + 1 < args.length) {
                    opts.put(a.substring(2), args[++i]);
                } else {
                    opts.put(a.substring(2), "true");
                }
            }
        }
        return opts;
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void printHelp() {
        List<String> lines = new ArrayList<>();
        lines.add("EvalCli — Medora Agent evaluation harness");
        lines.add("");
        lines.add("Options:");
        lines.add("  --cases <path>        Test case directory or YAML file (default: src/test/resources/eval/cases)");
        lines.add("  --report-dir <path>   Output report directory (default: target/eval/latest)");
        lines.add("  --mode <stub|replay|record|live>  Execution mode");
        lines.add("                          stub    = no LLM call, ActualBehavior from --scenarios or empty");
        lines.add("                          replay  = LlmJudge uses recordings (no network), needs --recordings");
        lines.add("                          record  = calls real LLM via env vars, records to --recordings (default src/test/resources/eval/recordings/judge.json)");
        lines.add("                                   env: AI_CHAT_API_KEY + AI_CHAT_MODEL (+ DEEPSEEK_OPENAI_BASE_URL, default deepseek)");
        lines.add("                          live    = bootstraps Spring + real AdkOrchestrator + DB (needs DB/LLM)");
        lines.add("  --recordings <path>   LLM recording JSON file (required for --mode=replay)");
        lines.add("  --scenarios <path>    YAML file mapping caseId → ActualBehavior (optional, for stub/replay)");
        lines.add("  --threshold <0..1>    Pass-rate threshold for exit code 0 (default: 1.0)");
        lines.add("  --category <CAT>      Filter by category (ROUTING/TRIAGE/BOOKING/KNOWLEDGE/SAFETY)");
        lines.add("  --help                Show this help");
        lines.forEach(System.out::println);
    }
}

package com.medora.eval.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 10 W4 — 从 YAML 文件读 caseId → ActualBehavior 映射的 harness。
 *
 * <p>用于在无真实 ADK 的前提下,验证 LlmJudge / 报告链路。
 * 配合 --mode=replay 一起用:
 * <ul>
 *   <li>scenarios 文件提供"假装是 agent 输出的"reply/transferChain/toolEvents</li>
 *   <li>recordings 文件提供 LlmJudge 调 LLM 的回放数据</li>
 * </ul>
 *
 * <p>YAML 格式:
 * <pre>
 * scenarios:
 *   routing-001:
 *     reply: "建议挂神经内科..."
 *     transferChain: [root_router_agent, triage_agent]
 *     toolEvents: []
 *     hallucinationRisk: LOW
 *     latencyMs: 250
 *   triage-001:
 *     reply: ...
 * </pre>
 *
 * <p>case 不在 scenarios 里时,返回 {@link EvalRun.ActualBehavior#empty()}。
 */
public class ScenarioAdkHarness implements AdkHarness {

    private final Map<String, EvalRun.ActualBehavior> scenarios;

    public ScenarioAdkHarness(Path yamlFile) throws IOException {
        this.scenarios = load(yamlFile.toFile());
    }

    public ScenarioAdkHarness(Map<String, EvalRun.ActualBehavior> scenarios) {
        this.scenarios = scenarios == null ? Map.of() : scenarios;
    }

    @Override
    public EvalRun.ActualBehavior run(TestCase tc) {
        return scenarios.getOrDefault(tc.id(), EvalRun.ActualBehavior.empty());
    }

    public int size() {
        return scenarios.size();
    }

    public boolean has(String caseId) {
        return scenarios.containsKey(caseId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EvalRun.ActualBehavior> load(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("scenario file not found: " + file.getAbsolutePath());
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> root = mapper.readValue(file, Map.class);
        Object scenariosNode = root.get("scenarios");
        if (!(scenariosNode instanceof Map)) {
            throw new IOException("scenario file must have top-level 'scenarios' map: " + file.getAbsolutePath());
        }
        Map<String, EvalRun.ActualBehavior> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) scenariosNode).entrySet()) {
            String caseId = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map)) continue;
            Map<String, Object> fields = (Map<String, Object>) value;
            result.put(caseId, toBehavior(fields));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static EvalRun.ActualBehavior toBehavior(Map<String, Object> fields) {
        String reply = (String) fields.getOrDefault("reply", "");
        List<String> chain = (List<String>) fields.getOrDefault("transferChain", List.of());
        List<Map<String, Object>> toolEvents = (List<Map<String, Object>>) fields.getOrDefault("toolEvents", List.of());
        String risk = (String) fields.getOrDefault("hallucinationRisk", "LOW");
        long latency = 0L;
        Object lat = fields.get("latencyMs");
        if (lat instanceof Number n) latency = n.longValue();
        return new EvalRun.ActualBehavior(reply, chain, toolEvents, risk, latency, null);
    }
}

# Medora 优化方案:架构收敛 + Agent 评测平台

> 起始日期:2026-07-08
> 当前分支:`feature/p10`
> 前置状态:Phase 9.7 已落地并通过 UI 联调(详见 [system-overview.md](../system-overview.md))

## 总体路线

| 阶段 | 周期 | 产出 | 状态 |
|------|------|------|------|
| **Phase 9.8** 架构收敛 | 2 周 | 双轨逻辑清零,代码统一为 ADK-only | ✅ 已完成 |
| **Phase 10 W1-W2** 评测平台骨架 | 1 周 | domain/loader/judge/runner/report + CLI stub 模式 | ✅ 已完成 |
| **Phase 10 W3-W4** LLM 评分 + 录制回放 | 1 周 | LlmJudge + record/replay + 对抗生成 + DiffReporter | ✅ 已完成 |
| **Phase 10 W4.5** 真实 ADK live harness | 3 天 | OrchestrateOneShotHarnessHelper + null-emitter 容忍 + EvalCli --mode=live | ✅ 已完成 |
| **Phase 10 W5** CI 接入 | 3 天 | .github/workflows/eval.yml(stub 模式 + 上传报告) | ✅ 已完成 |
| **Phase 10 W5.5** record 模式 | — | `--mode=record` CLI(环境变量读 key)+ `OpenAiCompatibleLlmClient` + 单测 | ✅ 已完成 |
| **Phase 10 W5.6** 后续 | — | recordings 入 git + 启用 eval-replay CI + 对抗生成 CLI 子命令 + LlmJudge 重跑稳定性 | ✅ 已完成 |

依赖关系:**Phase 10 评测的是真实生产路径**,所以必须等 9.8 完成才能开跑(否则在评测一段即将被删的代码)。

---

## Part 1 · Phase 9.8 架构收敛

### 1.1 双轨逻辑的精确范围

经代码勘察确认的收敛点:

**A. ChatOrchestrator 旧路径主体(~360 行)**
- `ChatOrchestrator.handle()` 中 line 90-191 的旧过程式编排整段删除
- 仅保留 line 79-89 的 delegation 逻辑
- 私有方法 `resolveIntent / hallucinationRisk / applyOutputSafety / filterUserProvidedParams / filterHighRiskFrequency / executableTools / publishDelta / splitDeltas` 全部删除

**B. 可整体删除的本地组件**
- `IntentRouter` + `IntentRouterTest` — ADK 用 `RootRouterAgent` 替代
- `PromptBuilder` + `PromptBuilderTest` — 仅 ChatOrchestrator 使用
- `AgentModelGateway` 的 4 个方法可删:
  - `planIntent` — ADK 用 RootRouterAgent 替代
  - `assessHallucination` — ADK 用 `LlmHallucinationAfterCallback` 替代
  - `checkMedicalAdvice` — ADK 用 `LlmOutputSafetyAfterCallback` 替代
  - `generateReplyStreaming` — ADK Runner 内部流式
- **保留**:`lightweightLlmCall` / `extractMemories` / `generateConversationSummary`(其他模块仍在用)

**C. 配置层**
- 移除 `medora.adk.enabled` 灰度开关
- 移除所有 `@ConditionalOnProperty` 条件加载
- `AdkOrchestrator` 改为无条件 `@Component`
- `ChatOrchestrator` 不再注入 `Optional<AdkOrchestrator>`,直接委托

### 1.2 执行步骤

| 步骤 | 周次 | 内容 | 状态 |
|------|------|------|------|
| 1. Metrics 上齐 | W1 | `MedoraMetricsService` 增加 `route_mode` tag(ADK/LOCAL),上报:路由准确率、工具成功率、首 token 延迟 P50/P95、总延迟、幻觉率、引用覆盖率、safety block 率 | ✅ |
| 2. 测试补齐 | W1.5 | 补 `AdkOrchestratorTest` 缺失的 transfer 场景 | ✅ |
| 3. 物理删除 | W2 | 一次性删除 A/B/C 三类代码 | ✅ |
| 4. 验证 | W2 | 全套测试通过 + UI 联调 + Metrics 无异常 | ✅ 测试 173/173 |

**实际改动**(2026-07-08 完成):
- 新增 `MedoraMetricsService.recordToolCall / recordSafetyOutcome / recordHallucinationRisk / recordRetrievalCitation`
- `AdkEventToSseAdapter` 在工具响应处上报 `recordToolCall`
- `AdkOrchestrator` 末尾上报 `recordHallucinationRisk + recordSafetyOutcome`
- 新增 3 个 transfer 场景测试(多跳路由、metrics 上报、safety block)
- 删除 `IntentRouter` + `IntentRouterTest`(本地关键词路由)
- 删除 `PromptBuilder` + `PromptBuilderTest`(本地 prompt 构建)
- 删除 `ToolExecutor`(本地工具执行器)
- 删除 `AgentModelGateway` 的 4 个方法:`planIntent / assessHallucination / checkMedicalAdvice / generateReplyStreaming`
- 删除 `AgentModelGatewayTest`(测试的目标方法已删)
- 重写 `ChatOrchestrator` 从 438 行 → 53 行(仅委托 ADK)
- 移除 `AdkProperties.enabled` 字段
- 移除 6 处 `@ConditionalOnProperty`(AdkOrchestrator / AdkRunnerConfig / DeepSeekModelConfig / AdkAgentTopology / PostgresBackedSessionService)
- 移除 `AdkOrchestrator / AdkAgentTopology / PostgresBackedSessionService` 中未使用的 `adkProperties` 字段
- 更新 `AdkConfigLoadTest`:删除"灰度关闭不加载"测试,改为"配置完整加载 + 缺 key/model fail-fast"
- 清理 `application.yml`:`medora.adk.enabled` 配置项移除

### 1.3 边界与风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 删 AgentModelGateway 方法破坏 SafetyGuard 等 | 编译失败 | 改前 `mvn compile` 验证,grep 确认保留方法边界 |
| 前端依赖 `safetyEventsJson` 字段格式 | SSE 协议回退 | `AdkEventToSseAdapter` 字段 schema 保持不变,仅删除旧路径的生成代码 |
| `adk-poc/` 目录混淆 | 代码库脏 | 9.8 顺手清理(确认无依赖后删除) |
| 旧路径 `applyOutputSafety` 免责声明逻辑被删 | 医疗合规风险 | `LlmOutputSafetyAfterCallback` 必须已覆盖等效逻辑,9.8 前对照测试 |

---

## Part 2 · Phase 10 Agent 评测与回归平台

### 2.1 设计原则

1. **CLI-first**:开发者本地能跑、能入 CI、产出 JSON+Markdown
2. **离线可重放**:case 入 git,LLM 调用支持 record/replay
3. **维度分层**:规则 Judge(确定性,免费)→ LLM Judge(语义,贵但准)
4. **对抗为主,种子兜底**:case 来源以 LLM 生成为主,但生成器必须有少量稳定种子 prompt 作为起点

### 2.2 数据模型

```java
// backend/src/main/java/com/medora/eval/domain/

record TestCase(
    String id,                          // stable, 用作 baseline key
    Category category,                  // ROUTING/TRIAGE/BOOKING/KNOWLEDGE/SAFETY
    String userMessage,
    ContextPreset context,              // userId / history / memoryOverrides / mockedNow
    Expectations expectations,
    Metadata metadata                   // severity / tags / source(LLM_GEN|SEED)
) {}

record Expectations(
    RouteExpectation route,             // 期望 transferChain,如 [root, booking]
    List<ToolCallExpectation> tools,    // [{toolName, paramMatchers, expectStatus}]
    List<ReplyMatcher> replyMatchers,   // contains / regex / not_contains / llm_judge / citation_count
    SafetyExpectation safety            // ALLOW / BLOCK / APPEND_DISCLAIMER
) {}

record EvalRun(
    UUID runId,
    Instant startedAt, finishedAt,
    String commitSha, String branch,
    Map<String,String> adkConfig,
    List<CaseResult> results,
    AggregateMetrics metrics,
    DiffVsBaseline diff
) {}
```

### 2.3 模块划分

```
backend/src/main/java/com/medora/eval/
├── domain/          # TestCase / EvalRun / CaseResult / Expectation
├── loader/          # YamlTestCaseLoader(从 resources 读 case 集)
├── runner/
│   ├── EvalRunner           # 主流程:load → run → judge → report
│   ├── AdkHarness           # 直接调 AdkOrchestrator.orchestrateOneShot
│   └── MockedToolRegistry   # 测试用可控工具响应
├── judge/
│   ├── Judge                       # interface
│   ├── RouteJudge                  # transferChain 精确匹配
│   ├── ToolCallJudge               # 工具名+参数+状态
│   ├── RegexJudge / ContainsJudge  # 确定性文本匹配
│   ├── CitationCoverageJudge       # 复用 CitationCoverageChecker
│   └── LlmJudge                    # 4 维评分(准确/简洁/有用/无害)
├── report/
│   ├── JsonReportWriter            # 结构化,入 baseline
│   ├── MarkdownReportWriter        # 人读,失败 case 高亮
│   └── DiffReporter                # 对比 baseline,标记 regression
└── generator/
    ├── AdversarialCaseGenerator    # LLM 生成对抗 case
    ├── SeedTemplates               # 5 类各 10 条种子 prompt
    └── MutationStrategy            # 同义改写 / 边界值 / 越权请求 / 注入攻击

backend/src/test/java/com/medora/eval/
├── EvalCli                         # main 入口
└── AdkHarnessTest                  # harness 自身的单元测试

backend/src/test/resources/eval/
├── cases/                  # 入 git
│   ├── routing-v1.yaml
│   ├── triage-v1.yaml
│   ├── booking-v1.yaml
│   ├── knowledge-v1.yaml
│   └── safety-v1.yaml
├── baselines/              # 入 git
│   └── main-2026-07-15.json
└── llm-replay/             # LLM 调用录制,保证可重放
```

### 2.4 CLI 入口

```bash
# 跑全部 case
mvn test-compile exec:java \
  -Dexec.mainClass=com.medora.eval.EvalCli \
  -Dexec.args="--cases src/test/resources/eval/cases --report target/eval/latest"

# 只跑某分类 + 强制规则 judge(免费,适合每次 PR)
mvn ... -Dexec.args="--category booking --judges regex,route,tool"

# 跑全套 + LLM judge(主线合并时)
mvn ... -Dexec.args="--judges regex,route,tool,llm,citation"

# 对比 baseline(回归检测)
mvn ... -Dexec.args="--baseline src/test/resources/eval/baselines/main.json --regression-threshold 0.05"

# 生成对抗 case(开发期人工抽审后入 git)
mvn ... -Dexec.args="--generate --count 50 --category routing"
```

退出码:`0` = 通过 / `1` = 存在回归 / `2` = 执行错误。

### 2.5 评测维度

| 维度 | Judge | 数据源 |
|------|------|--------|
| **路由准确率** | RouteJudge | `AdkStreamResult.transferChain` |
| **工具调用准确率** | ToolCallJudge | `toolEventsJson` |
| **检索相关性** | LlmJudge + CitationCoverage | `retrievalJson` |
| **回复质量** | LlmJudge(4 维) | `finalReply` |
| **安全通过率** | RegexJudge + 规则 | `safetyEventsJson` |
| **幻觉控制** | CitationCoverageJudge | `hallucinationRisk` |
| **性能** | (统计) | `latencyMs` 分布 P50/P95/P99 |
| **稳定性** | (统计) | 同 case 跑 5 次,方差 |

### 2.6 LLM-as-Judge 防抖动设计

case 生成和评分都用 LLM,需防循环偏置:

| 环节 | 模型 | 配置 |
|------|------|------|
| 对抗生成 | haiku(便宜快) | `temperature=0.9`,JSON Schema 强校验,失败丢弃 |
| 评分 judge | sonnet/opus | `temperature=0`,4 维独立评分(0-5),总分加权 |
| 评分稳定性 | — | 同 case 跑 3 次取中位数,方差 > 1.0 标记 `unstable` |
| 人工抽审 | — | 生成 case 入 git 前抽审 5-10% |

**baseline 锁定**:case 集 + LLM Judge prompt 一旦入 git,生成器版本变更不自动覆盖,需要 PR 显式更新 baseline。

### 2.7 与 ADK 集成关键点

```java
public class AdkHarness {
    private final AdkOrchestrator orchestrator;
    private final MockedToolRegistry mockedTools;
    
    public CaseResult run(TestCase tc) {
        // 1. 注入 mock 工具(替换真实 ToolDefinition)
        mockedTools.install(tc.context().toolMocks());
        
        // 2. 准备 conversation
        Long convId = testConversationFactory.create(tc.context());
        
        // 3. 调用 AdkOrchestrator.orchestrateOneShot(已存在,debug 用)
        AgentRunResult result = orchestrator.orchestrateOneShot(
            tc.context().userId(), convId, tc.userMessage()
        );
        
        // 4. 收集实际行为 + judge
        return CaseResult.of(tc, result, judge(tc, result));
    }
}
```

**LLM record/replay**:用 `LlmCallRecorder` 包装 `AgentModelGateway.lightweightLlmCall`,首次跑录制 prompt→response,后续按 prompt 哈希查表,保证 CI 可重放不调外部 API。

### 2.8 CI 集成

```yaml
# .github/workflows/eval.yml(或 Jenkinsfile)
on: [pull_request]
jobs:
  eval-fast:   # 每次 PR
    - 跑核心 case(routing/safety)
    - 只用确定性 judge
    - 阈值:passRate >= 95%
  eval-full:   # 主线合并
    - 跑全套 case
    - 启用 LLM Judge(用 recorded responses)
    - 对比 baseline,regression > 5% 阻断
```

---

## 时间盒与交付

| 周 | 阶段 | 任务 | 状态 |
|----|------|------|------|
| W1 | 9.8 | Metrics 加 route_mode tag + 业务指标上报 | ✅ |
| W2 | 9.8 | 补 AdkOrchestratorTest transfer 场景 + 物理删除旧路径 + UI 联调 | ✅ |
| W3 | 10 | EvalRunner + 3 个确定性 Judge + YAML loader + JSON/Markdown 报告 | ✅ |
| W4 | 10 | LlmJudge + 对抗生成器 + baseline diff + LLM record/replay + EvalCli replay 模式 | ✅ |
| W4.5 | 10 | OrchestrateOneShotHarnessHelper + null-emitter 容忍 + EvalCli --mode=live | ✅ |
| W5 | 10 | GitHub Actions eval.yml(stub 模式 CI) | ✅ |
| W5.5 | 10 | `--mode=record` CLI(env 读 key)+ `OpenAiCompatibleLlmClient` + 5 个单测 | ✅ |
| W5.6 | 10 | recordings 入 git + 启用 eval-replay CI + 对抗生成 CLI 子命令 + LlmJudge 稳定性 | ✅ |

### Phase 10 W1-W4 实际落地清单(2026-07-09 完成)

测试:205/205 通过(173 主线 + 32 eval)

**domain**(test scope):
- `TestCase`(嵌套 Category/ContextPreset/Expectations/RouteExpectation/ToolCallExpectation/ReplyMatcher/CaseMetadata)
- `EvalRun`(嵌套 CaseResult/ActualBehavior/Score/AggregateMetrics)

**loader**:
- `YamlTestCaseLoader`(支持多文档 YAML,用 MappingIterator)

**judge**:
- `Judge`(接口,supports 默认 true)
- `RouteJudge`(transferChain 严格相等)
- `ToolCallJudge`(工具名 + status 匹配)
- `ReplyMatcherJudge`(contains/not_contains/regex 确定性 matcher)
- `LlmJudge`(4 维评分 + harm 一票否决 + JSON 容错)

**runner**:
- `AdkHarness`(接口)
- `StubAdkHarness`(固定 ActualBehavior 桩)
- `ScenarioAdkHarness`(YAML 文件读 caseId → ActualBehavior)
- `EvalRunner`(主流程 + AggregateMetrics 聚合 + Judge 异常降级)

**llm**:
- `LlmClient`(接口)
- `RecordingLlmClient`(BiFunction 包装 + 文件缓存,首次跑写入)
- `ReplayingLlmClient`(从录制文件回放,未命中抛 ReplayMissException)
- `LlmRecorderStore` / `LlmCallRecord` / `PromptHasher`(SHA-256 截 16 字符)

**gen**:
- `SeedTemplates`(4 seed × 5 mutation)
- `AdversarialCaseGenerator`(seed × mutation → LLM → TestCase)
- `GeneratedCaseYamlWriter`(多文档 YAML 输出)

**report**:
- `JsonReportWriter`(Jackson pretty print + JavaTimeModule)
- `MarkdownReportWriter`(聚合 + 失败表 + 通过表)
- `DiffReporter`(REGRESSION/IMPROVEMENT/NEW_CASE/REMOVED_CASE + Markdown)

**CLI**:
- `EvalCli`(stub/replay/live,--threshold/--category/--help)
- 退出码 0/1/2

---

## 关键决策回顾

| 决策 | 选择 | 设计中的体现 |
|------|------|-------------|
| Phase 顺序 | 先收敛再评测 | 9.8 完成是 10 启动的前置条件 |
| 平台形态 | CLI + JSON/YAML | EvalCli + YamlTestCaseLoader,无 Web UI |
| 测试集来源 | LLM 对抗生成 | AdversarialCaseGenerator + 种子模板 + 人工抽审 5-10% |

---

## 变更日志

- **2026-07-08**:方案起草,9.8 启动 → 同日完成 9.8 全部改动(173/173 测试通过)
- **2026-07-09**:Phase 10 W1-W4 全部落地(205/205 测试通过)。EvalCli stub/replay 模式可用,live 模式待 W4.5。
- **2026-07-13**:Phase 10 W4.5 + W5 落地(210/210 测试通过)。
  - W4.5:`AdkEventToSseAdapter` 的 publishDelta/publishToolStatus 加 `emitter == null` 短路,让 `orchestrateOneShot` 可用作"无 SSE 输出"的 collect-only 路径;新增 `OrchestrateOneShotHarnessHelper` 静态辅助类(注入 AdkOrchestrator / ConversationRepository / UserAccountRepository);`EvalCli --mode=live` 启动 Spring + 装配 live harness。
  - W5:`.github/workflows/eval.yml` 跑 stub 模式 + 上传报告 artifact。replay 模式占位待 recordings 入 git 后启用。
- **2026-07-13**:Phase 10 W5.5 落地(215/215 测试通过)。
  - 新增 `OpenAiCompatibleLlmClient`(test scope,~80 行,java.net.http.HttpClient,POST {baseUrl}/chat/completions,temperature=0,解析 choices[0].message.content)。
  - `EvalCli --mode=record`:从环境变量读 `AI_CHAT_API_KEY` + `AI_CHAT_MODEL` + `DEEPSEEK_OPENAI_BASE_URL`(默认 https://api.deepseek.com/v1),用 RecordingLlmClient 包装写入 `--recordings`(默认 `src/test/resources/eval/recordings/judge.json`)。
  - 安全:不引入 `--llm-key` CLI 参数(避免 shell history 泄露),fail-fast 提示缺哪个环境变量。
  - 单测:5 个 `OpenAiCompatibleLlmClientTest`(JDK 自带 `HttpServer` 起 in-process stub,验证请求体 schema、URL、Authorization、响应解析、错误处理、trailing slash 剥离)。
  - demo-perfect.yaml 扩展到全部 11 个 case(原 3 个 → 11 个,覆盖 routing/triage/booking/knowledge/safety 全类别)。
  - **未启用** eval-replay CI job:需要先本地跑 --mode=record 生成 recordings.json,人工 review 后 commit 入 git,再取消 `.github/workflows/eval.yml` 中 eval-replay 的注释。eval.yml 注释块里写好了启用步骤。
- **2026-07-14**:Phase 10 W5.6 部分落地(recordings 入 git + eval-replay CI 启用,2/4 完成)。
  - 给 11 个 case 全部加 `type: llm_judge` replyMatcher(原 deterministic-only,接入 LlmJudge 4 维评分维度)。
  - 本地跑 `--mode=record` 11 次调用 DeepSeek,生成 `src/test/resources/eval/recordings/judge.json`(11 条 entry,全 1.0 高分,无敏感信息)。
  - 启用 `.github/workflows/eval.yml` 的 `eval-replay` job:从入 git recordings 回放 LlmJudge,threshold 0.7 容忍轻微抖动,报告上传 artifact。
  - **顺手修 bug**:`EvalCli.parseArgs` 不支持 `--key=value` 等号格式(原仅 `--key value` 空格),导致 Windows MINGW64 下 `-Dexec.args="--mode=record ..."` 全部 fallback 默认值。修复后两种格式都支持,加了 `equalsSyntaxIsAccepted` 回归测试。
  - **未完成**(剩余 2/4):对抗生成 CLI 子命令(`EvalCli --generate`)+ LlmJudge 重跑稳定性(同 case 跑 3 次取中位数,方差 > 1.0 标记 unstable)。
- **2026-07-14**:Phase 10 W5.6 全部落地(223/223 测试通过,W5.6 4/4 完成)。
  - `LlmJudge` 多 sample 聚合:加 `samples`/`stabilityThreshold` 字段 + 4 个构造器(默认 samples=1 向后兼容);samples>1 时取 median + 算 stddev,std > stabilityThreshold 在 reasoning 加 `[unstable: std=X.XX over N samples]` 标记。
  - `EvalCli` 新增 `--samples N` + `--stability-threshold X` 两个 CLI 参数。
  - `EvalCli` 新增 `--generate` 子命令:串通 `AdversarialCaseGenerator` + `GeneratedCaseYamlWriter`,支持 `--mode record|replay` + `--out <path>` + `--category <CAT>` 过滤,默认输出到 `target/eval/generated-cases.yaml`(reviewer 需补 expectations.route/tools 后才能入 cases/)。
  - **顺手修 parseArgs bug**:加 `BOOLEAN_FLAGS` set,boolean flag(`--generate`/`--help`)不消费下一个 token;原 bug 导致 `--generate --mode replay` 把 `--mode` 当成 generate 的 value。
  - 测试增量:`LlmJudgeTest` 10→15(+5:`multiSampleStableResponseAggregatesWithNoUnstableMarker` / `highVarianceAcrossSamplesMarkedUnstable` / `medianBelowThresholdFailsEvenIfOneSampleHigh` / `multiSampleShortCircuitsOnLlmFailure` / `samplesMustBePositive`);`EvalCliIntegrationTest` 6→8(+2:`generateWritesYamlFromReplayRecordings` / `generateRejectsInvalidMode`)。
  - **CI 升级**:`.github/workflows/eval.yml` 的 `actions/checkout` / `actions/setup-java` / `actions/upload-artifact` 全部 `@v4` → `@v7`(Node.js 20 deprecated,2026-09-16 完全移除)。

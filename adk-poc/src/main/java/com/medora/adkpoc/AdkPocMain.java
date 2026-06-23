package com.medora.adkpoc;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * ADK Java (0.3.0) + LangChain4j bridge + DeepSeek OpenAI-compatible endpoint POC.
 */
public class AdkPocMain {

    private static final String DEEPSEEK_API_KEY = requireEnv("DEEPSEEK_API_KEY");
    private static final String DEEPSEEK_BASE_URL = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1");
    private static final String DEEPSEEK_MODEL = System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-v4-flash");

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing env var: " + name + ". Set it before running: export DEEPSEEK_API_KEY=sk-...");
        }
        return v;
    }

    /** Strongly-typed tool input — ADK reads field names + @Schema descriptions to build the JSON schema. */
    public record DepartmentQuery(
            @Schema(description = "科室名称(如 心内科)或用户描述的症状关键词")
            String deptName
    ) {}

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        System.out.println("=== ADK Java (0.3.0) + LangChain4j + DeepSeek POC ===");
        System.out.println("Endpoint: " + DEEPSEEK_BASE_URL);
        System.out.println("Model:    " + DEEPSEEK_MODEL);
        System.out.println();

        OpenAiChatModel deepseek = OpenAiChatModel.builder()
                .apiKey(DEEPSEEK_API_KEY)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(DEEPSEEK_MODEL)
                .temperature(0.3)
                .maxTokens(800)
                .build();

        LlmAgent agent = LlmAgent.builder()
                .name("triage_bot")
                .description("医疗导诊助手")
                .model(new LangChain4j(deepseek))
                .instruction("""
                    你是医疗导诊助手。当用户询问某个科室看什么病、或描述症状询问该挂什么科时,
                    你必须调用 queryDepartmentInfo 工具查询科室信息,然后基于返回结果给出回答。
                    工具的 deptName 参数应该填入用户提到的科室名称或症状关键词(如"心内科"、"头痛"、"胸痛")。
                    不要凭记忆回答,必须先调工具。
                    """)
                .tools(FunctionTool.create(AdkPocMain.class, "queryDepartmentInfo"))
                .build();

        InMemoryRunner runner = new InMemoryRunner(agent);
        String appName = runner.appName();
        String userId = "poc-user-" + UUID.randomUUID();

        ConcurrentHashMap<String, Object> initialState = new ConcurrentHashMap<>();
        Session session = runner.sessionService()
                .createSession(appName, userId, initialState, null)
                .blockingGet();

        String[] queries = {
                "心内科主要看什么病?",
                "我一直头痛头晕,该挂什么科?"
        };

        for (String query : queries) {
            System.out.println("\n========== NEW TURN ==========");
            System.out.println("USER: " + query);
            runOneTurn(runner, session, userId, query);
        }
    }

    private static void runOneTurn(InMemoryRunner runner, Session session,
                                    String userId, String query) throws Exception {
        Content userMsg = Content.fromParts(Part.fromText(query));
        final boolean[] toolCalled = {false};
        final String[] toolArg = {null};
        final StringBuilder finalReply = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        final Throwable[] error = {null};

        runner.runAsync(userId, session.id(), userMsg)
                .subscribe(
                        event -> {
                            if (event.content().isEmpty()) return;
                            for (Part part : event.content().get().parts().orElse(List.of())) {
                                if (part.functionCall().isPresent()) {
                                    toolCalled[0] = true;
                                    var fc = part.functionCall().get();
                                    toolArg[0] = String.valueOf(fc.args());
                                    System.out.println("[TOOL CALL] name=" + fc.name()
                                            + " args=" + fc.args());
                                }
                                if (part.functionResponse().isPresent()) {
                                    var fr = part.functionResponse().get();
                                    System.out.println("[TOOL RESP] name=" + fr.name()
                                            + " response=" + fr.response());
                                }
                                if (part.text().isPresent() && !part.text().get().isEmpty()) {
                                    finalReply.append(part.text().get());
                                }
                            }
                        },
                        err -> { error[0] = err; done.countDown(); },
                        done::countDown
                );
        done.await();

        if (error[0] != null) {
            System.out.println("[ERROR] " + error[0]);
            error[0].printStackTrace();
            return;
        }

        System.out.println("--- ASSISTANT ---");
        System.out.println(finalReply);
        System.out.println("[VERDICT] tool_was_called=" + toolCalled[0]
                + " tool_args=" + toolArg[0]
                + " reply_chars=" + finalReply.length());
    }

    /** ADK tool — typed input via record, returns Map. */
    public static Map<String, Object> queryDepartmentInfo(DepartmentQuery query) {
        System.out.println("[TOOL EXEC] deptName = " + (query == null ? null : query.deptName()));
        String deptName = query == null || query.deptName() == null
                ? "" : query.deptName().toLowerCase();
        Map<String, Object> result = new HashMap<>();
        if (deptName.contains("心")) {
            result.put("deptName", "心内科");
            result.put("diseases", List.of("高血压", "冠心病", "心律失常", "心力衰竭"));
            result.put("recommendFor", "胸痛、心悸、胸闷、高血压、头晕伴心慌等症状");
        } else if (deptName.contains("神") || deptName.contains("头痛") || deptName.contains("头晕")) {
            result.put("deptName", "神经内科");
            result.put("diseases", List.of("脑卒中", "偏头痛", "癫痫", "帕金森"));
            result.put("recommendFor", "头痛、头晕、肢体麻木、言语不清、面瘫等症状");
        } else {
            result.put("deptName", deptName);
            result.put("diseases", List.of("暂无数据"));
            result.put("recommendFor", "请咨询导诊台");
        }
        return result;
    }
}

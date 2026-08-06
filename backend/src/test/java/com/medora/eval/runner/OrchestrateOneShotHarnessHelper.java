package com.medora.eval.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.adk.orchestrator.AdkOrchestrator;
import com.medora.agent.application.AgentRunResult;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Phase 10 W4.5 — 真实 ADK 路径 harness(辅助类形式)。
 *
 * <p>不直接实现 {@link AdkHarness},而是提供静态方法让 {@code EvalCli --mode=live}
 * 在 Spring 上下文起来后调用。这样:
 * <ul>
 *   <li>本类留在 test scope(和 eval 其他代码一起,不污染生产 jar)</li>
 *   <li>Spring bean(AdkOrchestrator / ConversationRepository / UserAccountRepository)由 EvalCli 注入</li>
 *   <li>无循环依赖</li>
 * </ul>
 *
 * <p>每次调用都创建一个新的 conversation,避免 case 之间状态污染。
 *
 * <p>前置条件(用户负责):
 * <ul>
 *   <li>DB 已起来且 flyway 迁移跑过</li>
 *   <li>case.context().userId() 对应的 user_account 行存在(默认 1)</li>
 *   <li>ADK 配置齐全(DeepSeek API key 等)</li>
 * </ul>
 */
public final class OrchestrateOneShotHarnessHelper {

    private static final Logger log = LoggerFactory.getLogger(OrchestrateOneShotHarnessHelper.class);

    private OrchestrateOneShotHarnessHelper() {}

    /**
     * 跑单个 TestCase,返回 ActualBehavior。
     */
    public static EvalRun.ActualBehavior runOnce(AdkOrchestrator orchestrator,
                                                 ConversationRepository conversationRepo,
                                                 UserAccountRepository userRepo,
                                                 ObjectMapper objectMapper,
                                                 TestCase tc) {
        Long userAccountId = tc.context() != null && tc.context().userId() != null
                ? tc.context().userId()
                : 1L;

        Long conversationId;
        try {
            conversationId = createConversation(conversationRepo, userRepo, userAccountId, tc);
        } catch (Exception ex) {
            log.error("case {}: failed to create conversation", tc.id(), ex);
            return errorBehavior("conversation creation failed: " + ex.getMessage());
        }

        long start = System.currentTimeMillis();
        AgentRunResult result;
        try {
            result = orchestrator.orchestrateOneShot(userAccountId, conversationId, tc.userMessage());
        } catch (Exception ex) {
            log.error("case {}: orchestrateOneShot threw", tc.id(), ex);
            return errorBehavior("orchestrate threw: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
        long elapsed = System.currentTimeMillis() - start;

        return toActualBehavior(objectMapper, result, elapsed);
    }

    /**
     * 创建一次性 conversation。
     *
     * <p>每个 case 一个新 conversation,避免 session state 互相干扰。
     * 不在 case 跑完后删除 — 让 review 阶段能在 DB 里看到对话历史便于调试。
     */
    private static Long createConversation(ConversationRepository repo,
                                           UserAccountRepository userRepo,
                                           Long userId,
                                           TestCase tc) {
        UserAccountEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "user_account id=" + userId + " not found — eval 需要 DB 里存在该 user,请先 seed"));
        ConversationEntity entity = ConversationEntity.create(
                user,
                "eval-" + tc.id(),
                "EVAL"
        );
        ConversationEntity saved = repo.save(entity);
        return saved.getId();
    }

    @SuppressWarnings("unchecked")
    private static EvalRun.ActualBehavior toActualBehavior(ObjectMapper mapper, AgentRunResult result, long elapsed) {
        String reply = result.reply() == null ? "" : result.reply();
        List<String> transferChain = List.of();
        List<Map<String, Object>> toolEvents = List.of();
        try {
            Map<String, Object> tcJson = mapper.readValue(result.toolCallJson(), new TypeReference<>() {});
            Object chain = tcJson.get("transferChain");
            if (chain instanceof List<?> list) {
                transferChain = list.stream().map(String::valueOf).toList();
            }
            Object tools = tcJson.get("toolEvents");
            if (tools instanceof List<?> list) {
                toolEvents = (List<Map<String, Object>>) (List<?>) list;
            }
        } catch (Exception ex) {
            log.warn("failed to parse toolCallJson, defaulting to empty chain/tools", ex);
        }
        return new EvalRun.ActualBehavior(
                reply,
                transferChain,
                toolEvents,
                result.hallucinationRisk() == null ? "LOW" : result.hallucinationRisk(),
                elapsed,
                null
        );
    }

    private static EvalRun.ActualBehavior errorBehavior(String message) {
        return new EvalRun.ActualBehavior(
                "", List.of(), List.of(), "HIGH", 0L, message
        );
    }
}

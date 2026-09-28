package com.medora.adk.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.sessions.Session;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostgresBackedSessionService 单测 — 用 mock ConversationRepository 验证:
 *  - createSession 正确把 state 写入 ConversationEntity.sessionState 并 save
 *  - getSession 正确从 ConversationEntity.sessionState 反序列化
 *  - 缺 conversation 时 getSession 返回 Maybe empty(用 blockingGet 抛 NoSuchElementException)
 *  - sessionId 必须 UUID 格式,否则抛 IllegalArgumentException
 *  - appName 固定为 "medora"
 *
 * 不涉及真实 Postgres / Redis — 这些在 9.3 集成测试中跑。
 */
class PostgresBackedSessionServiceTest {

    private ConversationRepository conversationRepository;
    private ChatMessageRepository chatMessageRepository;
    private PostgresBackedSessionService service;
    private UUID conversationPublicId;
    private ConversationEntity conversationEntity;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        service = new PostgresBackedSessionService(
                conversationRepository, chatMessageRepository, new ObjectMapper());

        conversationPublicId = UUID.randomUUID();
        conversationEntity = mock(ConversationEntity.class);
        UserAccountEntity userAccount = mock(UserAccountEntity.class);
        when(userAccount.getId()).thenReturn(42L);
        when(conversationEntity.getPublicId()).thenReturn(conversationPublicId);
        when(conversationEntity.getUserAccount()).thenReturn(userAccount);
    }

    @Test
    void createSessionPersistsStateToConversation() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 让 mock 实际存储 setSessionState 的入参,以便 captor 取出
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> stored =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return null;
        }).when(conversationEntity).setSessionState(any());
        org.mockito.Mockito.when(conversationEntity.getSessionState()).thenAnswer(inv -> stored.get());

        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("medora:userAccountId", 42L);
        state.put("medora:traceId", "trace-1");

        Session result = service.createSession("medora", "42", state, conversationPublicId.toString())
                .blockingGet();

        assertThat(result.id()).isEqualTo(conversationPublicId.toString());
        assertThat(result.appName()).isEqualTo("medora");
        assertThat(result.userId()).isEqualTo("42");
        assertThat(result.state()).containsEntry("medora:userAccountId", 42L);

        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionState()).containsEntry("medora:traceId", "trace-1");
    }

    @Test
    void createSessionWithMissingConversationThrows() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.empty());

        Single<Session> single = service.createSession(
                "medora", "42", new ConcurrentHashMap<>(), conversationPublicId.toString());

        assertThatThrownBy(single::blockingGet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conversation not found");
    }

    @Test
    void createSessionWithInvalidSessionIdThrows() {
        assertThatThrownBy(() ->
                service.createSession("medora", "42", new ConcurrentHashMap<>(), "not-a-uuid")
                        .blockingGet())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a UUID");
    }

    @Test
    void createSessionWithNullSessionIdThrows() {
        assertThatThrownBy(() ->
                service.createSession("medora", "42", new ConcurrentHashMap<>(), null)
                        .blockingGet())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires explicit sessionId");
    }

    @Test
    void getSessionReadsStateFromConversation() {
        Map<String, Object> stored = new HashMap<>();
        stored.put("medora:userAccountId", 42L);
        stored.put("custom:key", "value");
        when(conversationEntity.getSessionState()).thenReturn(stored);
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));

        Maybe<Session> maybe = service.getSession(
                "medora", "42", conversationPublicId.toString(), Optional.empty());

        Session result = maybe.blockingGet();
        assertThat(result).isNotNull();
        assertThat(result.state()).containsEntry("medora:userAccountId", 42L);
        assertThat(result.state()).containsEntry("custom:key", "value");
    }

    @Test
    void getSessionReturnsEmptyWhenConversationMissing() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.empty());

        Maybe<Session> maybe = service.getSession(
                "medora", "42", conversationPublicId.toString(), Optional.empty());

        // RxJava3 Maybe.blockingGet() on empty Maybe returns null(不抛异常)
        assertThat(maybe.blockingGet()).isNull();
        // 更精确:用 TestSubscriber 验证 onComplete 触发且无 value
        Maybe<Session> fresh = service.getSession(
                "medora", "42", conversationPublicId.toString(), Optional.empty());
        fresh.test().assertComplete().assertNoValues();
    }

    @Test
    void getSessionWithNullSessionStateReturnsEmptyMap() {
        when(conversationEntity.getSessionState()).thenReturn(null);
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));

        Session result = service.getSession(
                "medora", "42", conversationPublicId.toString(), Optional.empty()).blockingGet();
        assertThat(result.state()).isEmpty();
    }

    @Test
    void listSessionsReturnsEmptyListForNow() {
        List<Session> sessions = service.listSessions("medora", "42")
                .blockingGet()
                .sessions();
        assertThat(sessions).isEmpty();
    }

    @Test
    void deleteSessionIsNoOp() {
        service.deleteSession("medora", "42", conversationPublicId.toString())
                .blockingAwait();
        // no exception thrown = pass
    }

    @Test
    void listEventsReturnsEmptyWhenConversationMissing() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.empty());

        List<?> events = service.listEvents("medora", "42", conversationPublicId.toString())
                .blockingGet()
                .events();
        assertThat(events).isEmpty();
    }

    @Test
    void listEventsReturnsEmptyWhenNoMessages() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(List.of());

        List<?> events = service.listEvents("medora", "42", conversationPublicId.toString())
                .blockingGet()
                .events();
        assertThat(events).isEmpty();
    }

    @Test
    void listEventsReconstructsUserAndAssistantMessagesInOrder() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));
        // repository 倒序返回,service 内部 reverse 后给 LLM 看正序
        List<ChatMessageEntity> descMessages = new ArrayList<>();
        descMessages.add(msg("ASSISTANT", "请问挂哪个科?", OffsetDateTime.now().plusSeconds(2)));
        descMessages.add(msg("USER", "我想挂号", OffsetDateTime.now().plusSeconds(1)));
        descMessages.add(msg("USER", "你好", OffsetDateTime.now()));
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(descMessages);

        List<com.google.adk.events.Event> events = service.listEvents(
                        "medora", "42", conversationPublicId.toString())
                .blockingGet()
                .events();

        assertThat(events).hasSize(3);
        // 正序: USER "你好" → USER "我想挂号" → ASSISTANT "请问挂哪个科?"
        assertThat(events.get(0).author()).isEqualTo("user");
        assertThat(events.get(0).content().get().parts().get().get(0).text().get()).isEqualTo("你好");
        assertThat(events.get(1).author()).isEqualTo("user");
        assertThat(events.get(1).content().get().parts().get().get(0).text().get()).isEqualTo("我想挂号");
        assertThat(events.get(2).author()).isEqualTo("root_router_agent");
        assertThat(events.get(2).content().get().parts().get().get(0).text().get()).isEqualTo("请问挂哪个科?");
        // 所有 event 都标记为 turnComplete(完整轮次,不是 partial)
        assertThat(events).allSatisfy(e -> assertThat(e.turnComplete()).hasValue(true));
    }

    @Test
    void listEventsSkipsBlankAssistantPlaceholder() {
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));
        List<ChatMessageEntity> descMessages = new ArrayList<>();
        // 流式开始时会先插一条 content="" 的 assistant 占位消息,反构时应跳过
        descMessages.add(msg("ASSISTANT", "", OffsetDateTime.now().plusSeconds(2)));
        descMessages.add(msg("ASSISTANT", "   ", OffsetDateTime.now().plusSeconds(1)));
        descMessages.add(msg("USER", "你好", OffsetDateTime.now()));
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(descMessages);

        List<com.google.adk.events.Event> events = service.listEvents(
                        "medora", "42", conversationPublicId.toString())
                .blockingGet()
                .events();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).author()).isEqualTo("user");
    }

    @Test
    void createSessionMergesWithExistingState() {
        // 模拟上一轮已写入 slot 累积
        Map<String, Object> existingState = new HashMap<>();
        existingState.put("triageSymptoms", List.of("咳嗽", "流鼻涕"));
        existingState.put("triageRecommendedDept", "RESPIRATORY");
        when(conversationEntity.getSessionState()).thenReturn(existingState);
        when(conversationRepository.findByPublicId(conversationPublicId))
                .thenReturn(Optional.of(conversationEntity));

        // 这一轮的 per-request state
        ConcurrentHashMap<String, Object> incoming = new ConcurrentHashMap<>();
        incoming.put("medora:userAccountId", 42L);
        incoming.put("medora:userMessage", "帮我预约");

        service.createSession("medora", "42", incoming, conversationPublicId.toString())
                .blockingGet();

        // 直接 captor setSessionState 的入参,因为 save() 传入的是 mock entity,
        // 它的 getSessionState() 被 stub 成永远返回 existingState,无法反映 merge 结果
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(conversationEntity).setSessionState(stateCaptor.capture());
        Map<String, Object> saved = stateCaptor.getValue();
        // 已有的 slot 累积必须保留
        assertThat(saved).containsKey("triageSymptoms");
        assertThat(saved).containsKey("triageRecommendedDept");
        // 新的 per-request key 也写入
        assertThat(saved).containsEntry("medora:userAccountId", 42L);
        assertThat(saved).containsEntry("medora:userMessage", "帮我预约");
    }

    @Test
    void governedSessionUsesBundleInsteadOfDuplicatingThirtyLegacyEvents() {
        when(conversationRepository.findByPublicId(conversationPublicId)).thenReturn(Optional.of(conversationEntity));
        ChatMessageEntity historicalMessage = msg("USER", "早期信息", OffsetDateTime.now());
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(new ArrayList<>(List.of(historicalMessage)));
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("context.mode", "GOVERNED");
        state.put("context.summary", "早期信息");

        Session session = service.createSession("medora", "42", state, conversationPublicId.toString()).blockingGet();

        assertThat(session.events()).isEmpty();
        assertThat(service.listEvents("medora", "42", conversationPublicId.toString()).blockingGet().events())
                .hasSize(1);
    }

    @Test
    void legacySessionStillReconstructsHistoryAfterFeatureIsDisabled() {
        when(conversationRepository.findByPublicId(conversationPublicId)).thenReturn(Optional.of(conversationEntity));
        when(conversationEntity.getSessionState()).thenReturn(Map.of("context.mode", "GOVERNED"));
        ChatMessageEntity historicalMessage = msg("USER", "旧消息", OffsetDateTime.now());
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(new ArrayList<>(List.of(historicalMessage)));
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("context.mode", "LEGACY");

        Session session = service.createSession("medora", "42", state, conversationPublicId.toString()).blockingGet();

        assertThat(session.events()).hasSize(1);
    }

    @Test
    void legacySessionOmitsCurrentUserTurnAlreadyPersistedBeforeRunner() {
        when(conversationRepository.findByPublicId(conversationPublicId)).thenReturn(Optional.of(conversationEntity));
        ChatMessageEntity previous = msg("ASSISTANT", "上一轮回答", OffsetDateTime.now().minusSeconds(1));
        ChatMessageEntity current = msg("USER", "新问题", OffsetDateTime.now());
        when(chatMessageRepository.findTop30ByConversationOrderByCreatedAtDescIdDesc(conversationEntity))
                .thenReturn(new ArrayList<>(List.of(current, previous)));
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("context.mode", "LEGACY");
        state.put("medora:userMessage", "新问题");

        Session session = service.createSession("medora", "42", state, conversationPublicId.toString()).blockingGet();

        assertThat(session.events()).hasSize(1);
        assertThat(session.events().getFirst().author()).isEqualTo("root_router_agent");
    }

    private ChatMessageEntity msg(String role, String content, OffsetDateTime createdAt) {
        ChatMessageEntity m = mock(ChatMessageEntity.class);
        when(m.getRole()).thenReturn(role);
        when(m.getContent()).thenReturn(content);
        when(m.getCreatedAt()).thenReturn(createdAt);
        return m;
    }

    @Test
    void appNameIsMedora() {
        assertThat(service.appName()).isEqualTo("medora");
    }

    @Test
    void serializeAndDeserializeRoundtrip() {
        Map<String, Object> state = new HashMap<>();
        state.put("medora:userAccountId", 42L);
        state.put("medora:userMessage", "你好");
        state.put("nested", Map.of("a", 1, "b", "two"));

        String json = service.serializeState(state);
        Map<String, Object> parsed = service.deserializeState(json);

        assertThat(parsed).containsEntry("medora:userAccountId", 42);
        assertThat(parsed).containsEntry("medora:userMessage", "你好");
        assertThat(parsed.get("nested")).isInstanceOf(Map.class);
    }

    @Test
    void deserializeEmptyJsonReturnsEmptyMap() {
        assertThat(service.deserializeState(null)).isEmpty();
        assertThat(service.deserializeState("")).isEmpty();
        assertThat(service.deserializeState("   ")).isEmpty();
    }

    @Test
    void deserializeInvalidJsonThrows() {
        assertThatThrownBy(() -> service.deserializeState("{invalid json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to deserialize");
    }
}

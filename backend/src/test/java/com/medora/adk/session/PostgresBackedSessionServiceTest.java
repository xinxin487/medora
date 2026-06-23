package com.medora.adk.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.sessions.Session;
import com.medora.adk.config.AdkProperties;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private PostgresBackedSessionService service;
    private UUID conversationPublicId;
    private ConversationEntity conversationEntity;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        AdkProperties adkProperties = new AdkProperties();
        adkProperties.setEnabled(true);
        service = new PostgresBackedSessionService(
                conversationRepository, new ObjectMapper(), adkProperties);

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
    void listEventsReturnsEmptyForNow() {
        List<?> events = service.listEvents("medora", "42", conversationPublicId.toString())
                .blockingGet()
                .events();
        assertThat(events).isEmpty();
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

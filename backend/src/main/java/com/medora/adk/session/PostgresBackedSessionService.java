package com.medora.adk.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.State;
import com.medora.adk.config.AdkProperties;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ADK SessionService 的 Postgres 实现 — 把 ADK Session state 序列化为 JSONB 存入 conversation 表。
 *
 * 设计要点:
 *  - sessionId = conversation.publicId.toString()(UUID 字符串,跨系统安全)
 *  - userId = medora user account id(字符串形式)
 *  - appName 由 AdkProperties 注入(默认 "medora"),用于 Runner 与本 service 对齐
 *  - events():Phase 9.3 暂返回空列表,Phase 9.7 SSE 接入时再回填 chat_message → Event 映射
 *
 * RxJava3 的 Single/Maybe/Completable 用 Single.fromCallable / Maybe.fromCallable /
 * Completable.fromAction 桥接同步 JPA 调用。
 */
@Component
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class PostgresBackedSessionService implements BaseSessionService {

    private static final Logger log = LoggerFactory.getLogger(PostgresBackedSessionService.class);
    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {};

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final String appName;

    public PostgresBackedSessionService(ConversationRepository conversationRepository,
                                        ObjectMapper objectMapper,
                                        AdkProperties adkProperties) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.appName = "medora";
        log.info("PostgresBackedSessionService initialized. appName={}", appName);
    }

    @Override
    public Single<Session> createSession(String appName,
                                         String userId,
                                         ConcurrentMap<String, Object> state,
                                         String sessionId) {
        return Single.fromCallable(() -> {
            UUID conversationPublicId = parseOrRandomSessionId(sessionId);
            ConversationEntity entity = conversationRepository.findByPublicId(conversationPublicId)
                    .orElseThrow(() -> new IllegalStateException(
                            "cannot create ADK session: conversation not found, publicId=" + conversationPublicId));
            Map<String, Object> writable = state == null ? Map.of() : new ConcurrentHashMap<>(state);
            entity.setSessionState(writable);
            conversationRepository.save(entity);
            return toSession(entity, writable);
        });
    }

    @Override
    public Maybe<Session> getSession(String appName,
                                     String userId,
                                     String sessionId,
                                     Optional<GetSessionConfig> config) {
        return Maybe.create(emitter -> {
            try {
                UUID publicId = parseSessionId(sessionId);
                ConversationEntity entity = conversationRepository.findByPublicId(publicId).orElse(null);
                if (entity == null) {
                    emitter.onComplete();
                    return;
                }
                Map<String, Object> state = entity.getSessionState() == null
                        ? Collections.emptyMap()
                        : new ConcurrentHashMap<>(entity.getSessionState());
                emitter.onSuccess(toSession(entity, state));
            } catch (RuntimeException ex) {
                emitter.onError(ex);
            }
        });
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return Single.fromCallable(() -> ListSessionsResponse.builder()
                .sessions(List.of())
                .build());
    }

    @Override
    public Completable deleteSession(String appName, String userId, String sessionId) {
        return Completable.fromAction(() -> {
            // 不实际删除 conversation,只是符合接口签名
            log.debug("deleteSession called but is no-op (conversation retained). sessionId={}", sessionId);
        });
    }

    @Override
    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
        // Phase 9.3:暂返回空列表。Phase 9.7 时由 chat_message 表反向构造 Event。
        return Single.just(ListEventsResponse.builder()
                .events(java.util.Collections.<Event>emptyList())
                .build());
    }

    private Session toSession(ConversationEntity entity, Map<String, Object> state) {
        return Session.builder(appName)
                .id(entity.getPublicId().toString())
                .appName(appName)
                .userId(String.valueOf(entity.getUserAccount().getId()))
                .state(new State(new ConcurrentHashMap<>(state)))
                .events(Collections.emptyList())
                .lastUpdateTime(Instant.now())
                .build();
    }

    private UUID parseSessionId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId required");
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("sessionId must be a UUID, got: " + sessionId);
        }
    }

    private UUID parseOrRandomSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "PostgresBackedSessionService requires explicit sessionId (= conversation publicId)");
        }
        return parseSessionId(sessionId);
    }

    /** 测试/调试用:把 Map 序列化为 JSON 字符串(Phase 9.3 JPA 通过 jsonb 列自动序列化,本方法主要供测试) */
    public String serializeState(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize session state", e);
        }
    }

    public Map<String, Object> deserializeState(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, STATE_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize session state: " + json, e);
        }
    }

    public String appName() {
        return appName;
    }
}

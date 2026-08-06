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
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 *  - appName 固定 "medora"(与 Runner 对齐)
 *
 * **关键 — events 反构**(Phase 9.7.6 修复):
 *  ADK Runner.runAsync 内部不调 listEvents,它依赖 Session.events() 字段保留全部历史
 *  Event,然后通过 BaseSessionService.appendEvent 默认实现 `session.events().add(event)`
 *  做内存追加。所以 getSession/createSession 返回的 Session 必须自带历史 events,
 *  否则 LLM 拿不到任何对话上下文(上一轮用户说过的话全忘)。
 *
 *  实现方式:每次 getSession/createSession 时,从 chat_message 表反构最近 30 条历史消息
 *  为 ADK Event,填入 Session.events()。Runner 在本轮 appendEvent 加的 user/assistant
 *  Event 会在内存 list 上叠加,LLM prompt 拿到完整「历史 + 当前」上下文。下一轮请求来
 *  时 getSession 重新反构(此时上一轮的 user/assistant 已被 ChatRuntimeService 写入
 *  chat_message),自然延续。
 *
 * createSession 语义:upsert + merge(不是覆盖)。已存在的 sessionState 会先被读取,
 * 再把新传入的 state 合并进去(新 key 覆盖同 key,旧 key 保留)。这保证跨轮 slot
 * 累积不在每轮被重置。
 *
 * RxJava3 的 Single/Maybe/Completable 用 Single.fromCallable / Maybe.fromCallable /
 * Completable.fromAction 桥接同步 JPA 调用。
 */
@Component
public class PostgresBackedSessionService implements BaseSessionService {

    private static final Logger log = LoggerFactory.getLogger(PostgresBackedSessionService.class);
    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {};
    private static final String USER_AUTHOR = "user";
    private static final String ASSISTANT_AUTHOR = "root_router_agent";

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;
    private final String appName;

    public PostgresBackedSessionService(ConversationRepository conversationRepository,
                                        ChatMessageRepository chatMessageRepository,
                                        ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
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
            Map<String, Object> merged = mergeState(entity.getSessionState(), state);
            entity.setSessionState(merged);
            conversationRepository.save(entity);
            log.info("ADK createSession (merge) written. publicId={}, stateKeys={}, stateSize={}",
                    conversationPublicId, merged.keySet(), merged.size());
            return toSession(entity, merged);
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
                    log.info("ADK getSession miss (conversation not found). publicId={}", publicId);
                    emitter.onComplete();
                    return;
                }
                Map<String, Object> state = entity.getSessionState() == null
                        ? Collections.emptyMap()
                        : new ConcurrentHashMap<>(entity.getSessionState());
                log.info("ADK getSession hit. publicId={}, stateKeys={}, stateSize={}",
                        publicId, state.keySet(), state.size());
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
        return Completable.fromAction(() ->
                log.debug("deleteSession called but is no-op (conversation retained). sessionId={}", sessionId));
    }

    /**
     * 从 chat_message 表反向构造 ADK Event 列表(按时间正序)。
     *
     * **重要**:ADK Runner 不调 listEvents,这里实现主要给 web UI / debug 用。
     * 真正让 Runner 拿到历史的逻辑在 {@link #toSession} 内部调用 {@link #reconstructEvents}。
     */
    @Override
    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
        return Single.fromCallable(() -> {
            UUID publicId = parseSessionId(sessionId);
            ConversationEntity entity = conversationRepository.findByPublicId(publicId).orElse(null);
            if (entity == null) {
                log.info("ADK listEvents miss (conversation not found). publicId={}", publicId);
                return ListEventsResponse.builder().events(List.of()).build();
            }
            List<Event> events = reconstructEvents(entity);
            log.info("ADK listEvents reconstructed. publicId={}, events={}", publicId, events.size());
            return ListEventsResponse.builder().events(events).build();
        });
    }

    /**
     * 反构历史对话为 ADK Event 列表(按时间正序)。
     *
     * 共享给 listEvents 和 toSession — Runner 真正消费历史 events 的入口是 toSession。
     *
     * - role=USER      → author="user",            Content.role="user"
     * - role=ASSISTANT → author="root_router_agent", Content.role="model"
     * - 跳过空内容(流式开始时插的空 assistant 占位)
     * - 最多 30 条(避免 prompt token 爆炸)
     */
    private List<Event> reconstructEvents(ConversationEntity entity) {
        List<ChatMessageEntity> recent = chatMessageRepository
                .findTop30ByConversationOrderByCreatedAtDescIdDesc(entity);
        if (recent.isEmpty()) {
            return List.of();
        }
        Collections.reverse(recent);
        List<Event> events = new ArrayList<>(recent.size());
        for (ChatMessageEntity msg : recent) {
            String content = msg.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            boolean isUser = "USER".equalsIgnoreCase(msg.getRole());
            events.add(Event.builder()
                    .id(Event.generateEventId())
                    .author(isUser ? USER_AUTHOR : ASSISTANT_AUTHOR)
                    .content(com.google.genai.types.Content.builder()
                            .role(isUser ? USER_AUTHOR : "model")
                            .parts(List.of(com.google.genai.types.Part.fromText(content)))
                            .build())
                    .turnComplete(true)
                    .timestamp(toEpochMilli(msg.getCreatedAt()))
                    .build());
        }
        return events;
    }

    private Session toSession(ConversationEntity entity, Map<String, Object> state) {
        // events 必须用可变 list — BaseSessionService.appendEvent 默认实现是 session.events().add(event),
        // 用 Collections.emptyList() 会抛 UnsupportedOperationException。
        // CopyOnWriteArrayList 兼顾「可变」+「线程安全」(Runner 内部多线程订阅)。
        // 关键:list 初始就装入历史 chat_message 反构的 Event,否则 Runner 拿不到对话上下文。
        List<Event> history = reconstructEvents(entity);
        log.info("ADK toSession loaded history. publicId={}, historyEvents={}, stateSize={}",
                entity.getPublicId(), history.size(), state.size());
        return Session.builder(appName)
                .id(entity.getPublicId().toString())
                .appName(appName)
                .userId(String.valueOf(entity.getUserAccount().getId()))
                .state(new State(new ConcurrentHashMap<>(state)))
                .events(new java.util.concurrent.CopyOnWriteArrayList<>(history))
                .lastUpdateTime(Instant.now())
                .build();
    }

    /**
     * merge state — 保留已有 keys,新 state 覆盖同 key。
     * 这是 Phase 9.7 之后的修复 — 之前 createSession 直接 setSessionState 覆盖,
     * 导致每轮丢失上一轮的 slot 累积。
     */
    private Map<String, Object> mergeState(Map<String, Object> existing, ConcurrentMap<String, Object> incoming) {
        Map<String, Object> merged = existing == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(existing);
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    }

    private long toEpochMilli(OffsetDateTime time) {
        return time == null ? System.currentTimeMillis() : time.toInstant().toEpochMilli();
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

    /** 测试/调试用:把 Map 序列化为 JSON 字符串(JPA 通过 jsonb 列自动序列化,本方法主要供测试) */
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

package com.medora.agent.trace;

import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_trace")
public class AgentTraceEntity extends BaseEntity {

    @Column(name = "trace_id", nullable = false, unique = true)
    private String traceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ChatMessageEntity message;

    @Column(name = "intent_type", nullable = false)
    private String intentType;

    @Column(name = "route_mode", nullable = false)
    private String routeMode;

    @Column(name = "retrieval_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String retrievalJson = "[]";

    @Column(name = "tool_events_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String toolEventsJson = "[]";

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    public static AgentTraceEntity of(String traceId,
                                      ConversationEntity conversation,
                                      ChatMessageEntity message,
                                      String intentType,
                                      String routeMode,
                                      String retrievalJson,
                                      String toolEventsJson,
                                      long latencyMs,
                                      String status,
                                      String errorMessage) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.traceId = traceId;
        entity.conversation = conversation;
        entity.message = message;
        entity.intentType = intentType;
        entity.routeMode = routeMode;
        entity.retrievalJson = retrievalJson == null || retrievalJson.isBlank() ? "[]" : retrievalJson;
        entity.toolEventsJson = toolEventsJson == null || toolEventsJson.isBlank() ? "[]" : toolEventsJson;
        entity.latencyMs = latencyMs;
        entity.status = status;
        entity.errorMessage = errorMessage;
        return entity;
    }

    public String getTraceId() {
        return traceId;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public ChatMessageEntity getMessage() {
        return message;
    }

    public String getIntentType() {
        return intentType;
    }

    public String getRouteMode() {
        return routeMode;
    }

    public String getRetrievalJson() {
        return retrievalJson;
    }

    public String getToolEventsJson() {
        return toolEventsJson;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

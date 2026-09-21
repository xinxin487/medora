package com.medora.toolgateway.audit;

import com.medora.common.domain.BaseEntity;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tool_gateway_audit")
public class ToolAuditEntity extends BaseEntity {

    @Column(name = "user_id") private Long userId;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(name = "tool_version", nullable = false) private String toolVersion;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "risk_level", nullable = false) private String riskLevel;
    @Column(name = "agent_name") private String agentName;
    @Column(name = "conversation_id") private String conversationId;
    @Column(name = "trace_id") private String traceId;
    @Column(name = "request_id") private String requestId;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "error_code") private String errorCode;
    @Column(name = "duration_ms", nullable = false) private long durationMs;
    @Column(name = "arguments_hash", nullable = false, length = 64) private String argumentsHash;
    @Column(name = "arguments_summary", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String argumentsSummary;

    public static ToolAuditEntity of(ToolDescriptor descriptor, GatewayToolRequest request,
                                     GatewayToolResult result, long durationMs,
                                     ToolAuditSanitizer.SanitizedArguments arguments) {
        ToolAuditEntity entity = new ToolAuditEntity();
        entity.userId = request.context().userId();
        entity.toolName = descriptor.name();
        entity.toolVersion = descriptor.version();
        entity.sourceType = descriptor.sourceType().name();
        entity.riskLevel = descriptor.riskLevel().name();
        entity.agentName = request.context().agentName();
        entity.conversationId = request.context().conversationId();
        entity.traceId = request.context().traceId();
        entity.requestId = request.context().requestId();
        entity.status = result.status().name();
        entity.errorCode = result.errorCode() == null ? null : result.errorCode().name();
        entity.durationMs = Math.max(0, durationMs);
        entity.argumentsHash = arguments.argumentsHash();
        entity.argumentsSummary = "{}";
        return entity;
    }

    void setArgumentsSummary(String argumentsSummary) { this.argumentsSummary = argumentsSummary; }
    public String getToolName() { return toolName; }
    public String getToolVersion() { return toolVersion; }
    public String getSourceType() { return sourceType; }
    public String getRiskLevel() { return riskLevel; }
    public String getAgentName() { return agentName; }
    public String getConversationId() { return conversationId; }
    public String getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Long getDurationMs() { return durationMs; }
    public String getTraceId() { return traceId; }
    public String getRequestId() { return requestId; }
    public String getArgumentsHash() { return argumentsHash; }
    public String getArgumentsSummary() { return argumentsSummary; }
}

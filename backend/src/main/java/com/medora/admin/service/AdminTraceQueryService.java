package com.medora.admin.service;

import com.medora.admin.api.response.AdminAgentTraceDetailResponse;
import com.medora.admin.api.response.AdminAgentTraceItem;
import com.medora.agent.trace.AgentTraceEntity;
import com.medora.agent.trace.AgentTraceRepository;
import com.medora.common.api.PageResponse;
import com.medora.common.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTraceQueryService {

    private final AgentTraceRepository agentTraceRepository;

    public AdminTraceQueryService(AgentTraceRepository agentTraceRepository) {
        this.agentTraceRepository = agentTraceRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAgentTraceItem> list(String traceId,
                                                  String conversationId,
                                                  String status,
                                                  String routeMode,
                                                  String intentType,
                                                  Integer pageNo,
                                                  Integer pageSize) {
        Page<AdminAgentTraceItem> page = agentTraceRepository.findAll(
                toSpec(traceId, conversationId, status, routeMode, intentType),
                PageRequest.of(
                        normalizePageNo(pageNo) - 1,
                        normalizePageSize(pageSize),
                        Sort.by(Sort.Order.desc("createdAt"))
                )
        ).map(this::toItem);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public AdminAgentTraceDetailResponse get(String traceId) {
        AgentTraceEntity trace = agentTraceRepository.findByTraceId(traceId)
                .orElseThrow(() -> new BusinessException("AGENT_TRACE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "agent trace not found"));
        return new AdminAgentTraceDetailResponse(
                trace.getTraceId(),
                trace.getConversation().getPublicId().toString(),
                trace.getMessage() == null ? null : trace.getMessage().getPublicId().toString(),
                trace.getIntentType(),
                trace.getRouteMode(),
                trace.getStatus(),
                trace.getLatencyMs() == null ? 0 : trace.getLatencyMs(),
                trace.getErrorMessage(),
                trace.getRetrievalJson(),
                trace.getToolEventsJson(),
                trace.getSafetyEventsJson(),
                trace.getHallucinationRisk(),
                trace.getRiskFactorsJson(),
                trace.getCreatedAt()
        );
    }

    private Specification<AgentTraceEntity> toSpec(String traceId,
                                                   String conversationId,
                                                   String status,
                                                   String routeMode,
                                                   String intentType) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(traceId)) {
                predicates.add(builder.equal(root.get("traceId"), traceId.trim()));
            }
            if (StringUtils.hasText(conversationId)) {
                predicates.add(builder.equal(root.join("conversation").get("publicId"), parseConversationId(conversationId)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(builder.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (StringUtils.hasText(routeMode)) {
                predicates.add(builder.equal(root.get("routeMode"), routeMode.trim().toUpperCase()));
            }
            if (StringUtils.hasText(intentType)) {
                predicates.add(builder.equal(root.get("intentType"), intentType.trim().toUpperCase()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminAgentTraceItem toItem(AgentTraceEntity entity) {
        return new AdminAgentTraceItem(
                entity.getTraceId(),
                entity.getConversation().getPublicId().toString(),
                entity.getMessage() == null ? null : entity.getMessage().getPublicId().toString(),
                entity.getIntentType(),
                entity.getRouteMode(),
                entity.getStatus(),
                entity.getLatencyMs() == null ? 0 : entity.getLatencyMs(),
                entity.getErrorMessage(),
                entity.getHallucinationRisk(),
                entity.getCreatedAt()
        );
    }

    private UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId);
        } catch (RuntimeException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "conversationId must be a valid uuid");
        }
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }
}

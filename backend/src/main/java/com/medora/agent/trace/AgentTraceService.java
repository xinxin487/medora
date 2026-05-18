package com.medora.agent.trace;

import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.common.metrics.MedoraMetricsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTraceService {

    private final AgentTraceRepository agentTraceRepository;
    private final MedoraMetricsService medoraMetricsService;

    public AgentTraceService(AgentTraceRepository agentTraceRepository,
                             MedoraMetricsService medoraMetricsService) {
        this.agentTraceRepository = agentTraceRepository;
        this.medoraMetricsService = medoraMetricsService;
    }

    @Transactional
    public void record(AgentTraceRecord record) {
        agentTraceRepository.save(AgentTraceEntity.of(
                record.traceId(),
                record.conversation(),
                record.message(),
                record.intentType(),
                record.routeMode(),
                record.retrievalJson(),
                record.toolEventsJson(),
                record.latencyMs(),
                record.status(),
                record.errorMessage()
        ));
        medoraMetricsService.recordAgentTrace(
                record.intentType(),
                record.routeMode(),
                record.status(),
                record.latencyMs()
        );
    }

    public record AgentTraceRecord(
            String traceId,
            ConversationEntity conversation,
            ChatMessageEntity message,
            String intentType,
            String routeMode,
            String retrievalJson,
            String toolEventsJson,
            long latencyMs,
            String status,
            String errorMessage
    ) {
    }
}

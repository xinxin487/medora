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
                record.errorMessage(),
                record.safetyEventsJson(),
                record.hallucinationRisk(),
                record.riskFactorsJson()
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
            String errorMessage,
            String safetyEventsJson,
            String hallucinationRisk,
            String riskFactorsJson
    ) {
        public AgentTraceRecord {
            safetyEventsJson = safetyEventsJson == null || safetyEventsJson.isBlank() ? "[]" : safetyEventsJson;
            hallucinationRisk = hallucinationRisk == null || hallucinationRisk.isBlank() ? "LOW" : hallucinationRisk;
            riskFactorsJson = riskFactorsJson == null || riskFactorsJson.isBlank() ? "[]" : riskFactorsJson;
        }
    }
}

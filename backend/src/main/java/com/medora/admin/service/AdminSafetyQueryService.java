package com.medora.admin.service;

import com.medora.agent.trace.AgentTraceEntity;
import com.medora.agent.trace.AgentTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSafetyQueryService {

    private final AgentTraceRepository agentTraceRepository;
    private final Clock clock;

    public AdminSafetyQueryService(AgentTraceRepository agentTraceRepository, Clock clock) {
        this.agentTraceRepository = agentTraceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SafetyStatsResponse getSafetyStats() {
        // 查询最近7天的 trace 记录
        List<AgentTraceEntity> allRecent = agentTraceRepository.findAll();
        LocalDate cutoff = LocalDate.now(clock).minusDays(7);
        List<AgentTraceEntity> recent = allRecent.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().isAfter(cutoff))
                .toList();

        // 按日统计安全拦截次数
        Map<LocalDate, Long> dailyBlocks = new LinkedHashMap<>();
        for (AgentTraceEntity trace : recent) {
            if ("SAFETY_REJECTED".equals(trace.getStatus())) {
                LocalDate day = trace.getCreatedAt().toLocalDate();
                dailyBlocks.merge(day, 1L, Long::sum);
            }
        }

        // 幻觉风险分布
        long lowRisk = 0, mediumRisk = 0, highRisk = 0;
        for (AgentTraceEntity trace : recent) {
            String risk = trace.getHallucinationRisk();
            if ("HIGH".equals(risk)) highRisk++;
            else if ("MEDIUM".equals(risk)) mediumRisk++;
            else lowRisk++;
        }

        // 高风险 trace 列表
        List<String> highRiskTraceIds = recent.stream()
                .filter(t -> "HIGH".equals(t.getHallucinationRisk()))
                .map(AgentTraceEntity::getTraceId)
                .limit(10)
                .toList();

        // 工具调用成功率
        long totalToolCalls = 0, successToolCalls = 0;
        for (AgentTraceEntity trace : recent) {
            if (trace.getToolEventsJson() != null && !"[]".equals(trace.getToolEventsJson())) {
                totalToolCalls++;
                if ("SUCCESS".equals(trace.getStatus())) {
                    successToolCalls++;
                }
            }
        }
        double toolSuccessRate = totalToolCalls > 0 ? (double) successToolCalls / totalToolCalls : 1.0;

        return new SafetyStatsResponse(
                dailyBlocks,
                Map.of("LOW", lowRisk, "MEDIUM", mediumRisk, "HIGH", highRisk),
                (long) recent.size(),
                highRiskTraceIds,
                toolSuccessRate,
                totalToolCalls
        );
    }

    public record SafetyStatsResponse(
            Map<LocalDate, Long> dailySafetyBlocks,
            Map<String, Long> hallucinationRiskDistribution,
            long totalRecentTraces,
            List<String> highRiskTraceIds,
            double toolCallSuccessRate,
            long totalToolCalls
    ) {
    }
}

package com.medora.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.admin.api.response.AdminContextDiagnosticResponse;
import com.medora.agent.trace.AgentTraceEntity;
import com.medora.agent.trace.AgentTraceRepository;
import com.medora.agent.context.ContextDecision;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminContextDiagnosticService {
    private final AgentTraceRepository traces; private final ObjectMapper objectMapper;
    public AdminContextDiagnosticService(AgentTraceRepository traces, ObjectMapper objectMapper) { this.traces = traces; this.objectMapper = objectMapper; }
    @Transactional(readOnly = true)
    public AdminContextDiagnosticResponse get(String traceId) {
        AgentTraceEntity trace = traces.findByTraceId(traceId).orElseThrow(() -> new BusinessException("AGENT_TRACE_NOT_FOUND", HttpStatus.NOT_FOUND, "agent trace not found"));
        Map<String,Object> raw;
        try { raw = objectMapper.readValue(trace.getContextDiagnosticJson(), new TypeReference<>() {}); } catch (Exception ignored) { raw = Map.of(); }
        return new AdminContextDiagnosticResponse(traceId, String.valueOf(raw.getOrDefault("profile", "GENERAL")),
                intMap(raw.get("budget")), intMap(raw.get("sourceCounts")), intMap(raw.get("reasonCounts")),
                strings(raw.get("itemFingerprints")), decisions(raw.get("decisions")),
                Boolean.TRUE.equals(raw.get("degraded")));
    }
    private Map<String,Integer> intMap(Object value) { if (!(value instanceof Map<?,?> map)) return Map.of(); Map<String,Integer> result = new LinkedHashMap<>(); map.forEach((k,v) -> { if (v instanceof Number n) result.put(String.valueOf(k), n.intValue()); }); return Map.copyOf(result); }
    private List<String> strings(Object value) { if (!(value instanceof List<?> list)) return List.of(); return list.stream().map(String::valueOf).toList(); }
    private List<ContextDecision> decisions(Object value) {
        if (!(value instanceof List<?>)) return List.of();
        try { return objectMapper.convertValue(value, new TypeReference<List<ContextDecision>>() {}); }
        catch (IllegalArgumentException ignored) { return List.of(); }
    }
}

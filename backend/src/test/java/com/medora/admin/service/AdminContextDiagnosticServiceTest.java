package com.medora.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.trace.AgentTraceEntity;
import com.medora.agent.trace.AgentTraceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminContextDiagnosticServiceTest {
    @Test
    void associatesEachSanitizedMemoryFingerprintWithItsDecision() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        AgentTraceEntity trace = mock(AgentTraceEntity.class);
        when(repository.findByTraceId("trace-1")).thenReturn(Optional.of(trace));
        when(trace.getContextDiagnosticJson()).thenReturn("""
                {"profile":"BOOKING","budget":{"allocated":8000,"used":600},
                 "sourceCounts":{"MEMORY":1},"reasonCounts":{"EXPIRED":1,"SELECTED":1},
                 "itemFingerprints":["hash-a","hash-b"],"degraded":false,
                 "decisions":[
                   {"source":"MEMORY","fingerprint":"hash-a","estimatedTokens":30,"selected":false,"reasonCode":"EXPIRED"},
                   {"source":"MEMORY","fingerprint":"hash-b","estimatedTokens":40,"selected":true,"reasonCode":"SELECTED"}]}
                """);

        var response = new AdminContextDiagnosticService(repository, new ObjectMapper()).get("trace-1");

        assertThat(response.decisions()).hasSize(2);
        assertThat(response.decisions().getFirst().fingerprint()).isEqualTo("hash-a");
        assertThat(response.decisions().getFirst().reasonCode()).isEqualTo("EXPIRED");
        assertThat(response.toString()).doesNotContain("rawValue", "用户偏好");
    }
}

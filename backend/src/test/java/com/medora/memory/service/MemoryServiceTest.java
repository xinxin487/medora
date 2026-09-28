package com.medora.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentModelGateway;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.memory.domain.UserMemoryProfileEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryServiceTest {

    @Test
    void contextCandidatesKeepActiveMemoriesAheadOfRejectedHistory() {
        UserMemoryProfileRepository repository = mock(UserMemoryProfileRepository.class);
        UserMemoryProfileEntity active = mock(UserMemoryProfileEntity.class);
        UserMemoryProfileEntity inactive = mock(UserMemoryProfileEntity.class);
        when(active.getMemoryType()).thenReturn("PREFERRED_PERIOD");
        when(active.getSummary()).thenReturn("偏好上午");
        when(active.getConfidence()).thenReturn(java.math.BigDecimal.valueOf(.9));
        when(active.getSource()).thenReturn("USER_EXPLICIT");
        when(active.getStatus()).thenReturn("ACTIVE");
        when(inactive.getMemoryType()).thenReturn("PREFERRED_PERIOD");
        when(inactive.getSummary()).thenReturn("旧的下午偏好");
        when(inactive.getConfidence()).thenReturn(java.math.BigDecimal.valueOf(.5));
        when(inactive.getSource()).thenReturn("USER_MENTION");
        when(inactive.getStatus()).thenReturn("INACTIVE");
        when(repository.findTop100ByUserAccountIdAndStatusOrderByUpdatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of(active));
        when(repository.findTop100ByUserAccountIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(inactive, active));
        when(active.isActive()).thenReturn(true);
        MemoryService service = new MemoryService(repository, new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper(), mock(AgentModelGateway.class));

        var candidates = service.loadContextCandidates(1L);

        org.assertj.core.api.Assertions.assertThat(candidates).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(candidates.getFirst().status()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(candidates.getLast().status()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldWriteStableMemoriesWhenAppointmentBookedSuccessfully() {
        UserMemoryProfileRepository repository = mock(UserMemoryProfileRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.extractMemories(anyString())).thenReturn(Optional.empty());
        MemoryService service = new MemoryService(
                repository,
                new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper(),
                gateway
        );
        UserAccountEntity userAccount = new UserAccountEntity();
        when(repository.findByUserAccountAndMemoryTypeAndMemoryKey(eq(userAccount), anyString(), anyString()))
                .thenReturn(Optional.empty());

        AppointmentResponse appointment = new AppointmentResponse(
                "apt-001",
                "APT202605180001",
                "CONFIRMED",
                OffsetDateTime.now(),
                new AppointmentResponse.Schedule("slot-001", "2026-05-19", "PM"),
                new AppointmentResponse.Department("dept-001", "呼吸内科"),
                new AppointmentResponse.Doctor("doctor-001", "李医生")
        );
        AgentRunResult runResult = new AgentRunResult(
                new IntentDecision("BOOK_APPOINTMENT", RouteMode.TOOL_ONLY, false, List.of()),
                "预约成功",
                List.of(),
                List.of(ToolExecutionResult.success(ToolNames.BOOK_APPOINTMENT, appointment, "预约成功")),
                "[]",
                "{}",
                "{}",
                "[]",
                "LOW",
                "[]"
        );

        service.captureFromAgentRun(userAccount, "帮我预约这个下午号", runResult);

        ArgumentCaptor<UserMemoryProfileEntity> captor = ArgumentCaptor.forClass(UserMemoryProfileEntity.class);
        verify(repository, times(4)).save(captor.capture());
        List<String> memoryTypes = captor.getAllValues().stream()
                .map(UserMemoryProfileEntity::getMemoryType)
                .collect(Collectors.toList());

        assertTrue(memoryTypes.contains(MemoryService.MEMORY_TYPE_PREFERRED_DEPARTMENT));
        assertTrue(memoryTypes.contains(MemoryService.MEMORY_TYPE_PREFERRED_DOCTOR));
        assertTrue(memoryTypes.contains(MemoryService.MEMORY_TYPE_PREFERRED_PERIOD));
        assertTrue(memoryTypes.contains(MemoryService.MEMORY_TYPE_LAST_SUCCESSFUL_APPOINTMENT));
    }
}

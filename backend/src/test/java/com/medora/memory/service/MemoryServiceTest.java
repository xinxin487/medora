package com.medora.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void shouldWriteStableMemoriesWhenAppointmentBookedSuccessfully() {
        UserMemoryProfileRepository repository = mock(UserMemoryProfileRepository.class);
        MemoryService service = new MemoryService(
                repository,
                new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper()
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

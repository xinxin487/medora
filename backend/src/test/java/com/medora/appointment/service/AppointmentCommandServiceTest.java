package com.medora.appointment.service;

import com.medora.appointment.api.request.CancelAppointmentRequest;
import com.medora.appointment.api.request.CreateAppointmentRequest;
import com.medora.appointment.domain.AppointmentEntity;
import com.medora.appointment.domain.AppointmentOperationLogEntity;
import com.medora.appointment.repository.AppointmentOperationLogRepository;
import com.medora.appointment.repository.AppointmentRepository;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.audit.AuditLogService;
import com.medora.common.metrics.MedoraMetricsService;
import com.medora.masterdata.domain.DepartmentEntity;
import com.medora.masterdata.domain.DoctorEntity;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentCommandServiceTest {

    private CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private UserAccountRepository userAccountRepository;
    private ScheduleSlotRepository scheduleSlotRepository;
    private AppointmentRepository appointmentRepository;
    private AppointmentOperationLogRepository operationLogRepository;
    private AuditLogService auditLogService;
    private MedoraMetricsService medoraMetricsService;
    private AppointmentCommandService service;

    @BeforeEach
    void setUp() {
        currentAuthenticatedUserProvider = mock(CurrentAuthenticatedUserProvider.class);
        userAccountRepository = mock(UserAccountRepository.class);
        scheduleSlotRepository = mock(ScheduleSlotRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        operationLogRepository = mock(AppointmentOperationLogRepository.class);
        auditLogService = mock(AuditLogService.class);
        medoraMetricsService = mock(MedoraMetricsService.class);
        service = new AppointmentCommandService(
                currentAuthenticatedUserProvider,
                userAccountRepository,
                scheduleSlotRepository,
                appointmentRepository,
                operationLogRepository,
                auditLogService,
                medoraMetricsService
        );
    }

    @Test
    void shouldCreateAppointmentAndReserveSlotQuota() {
        UUID userId = UUID.randomUUID();
        UserAccountEntity user = buildUser(userId);
        ScheduleSlotEntity slot = buildSlot(2, "OPEN");

        when(currentAuthenticatedUserProvider.getRequiredUser()).thenReturn(new AuthenticatedUser(userId, java.util.List.of("USER")));
        when(userAccountRepository.findByPublicId(userId)).thenReturn(Optional.of(user));
        when(scheduleSlotRepository.findByPublicIdForUpdate(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(appointmentRepository.existsByUserAccountAndSlotAndStatus(user, slot, "CONFIRMED")).thenReturn(false);
        when(appointmentRepository.save(any(AppointmentEntity.class))).thenAnswer(invocation -> {
            AppointmentEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L);
            ReflectionTestUtils.setField(entity, "publicId", UUID.randomUUID());
            return entity;
        });

        var response = service.create(
                new CreateAppointmentRequest(slot.getPublicId().toString(), "need morning"),
                "idem-001"
        );

        assertNotNull(response.appointmentId());
        assertEquals("CONFIRMED", response.status());
        assertEquals(1, slot.getAvailableQuota());
        verify(operationLogRepository).save(any(AppointmentOperationLogEntity.class));
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
        verify(medoraMetricsService).recordAppointmentCommand("CREATE", "SUCCESS");
    }

    @Test
    void shouldReturnExistingAppointmentForSameIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        UserAccountEntity user = buildUser(userId);
        ScheduleSlotEntity slot = buildSlot(2, "OPEN");
        AppointmentEntity existing = AppointmentEntity.confirmed(user, slot, "APT202604220001", "idem-001", null);
        ReflectionTestUtils.setField(existing, "publicId", UUID.randomUUID());

        when(currentAuthenticatedUserProvider.getRequiredUser()).thenReturn(new AuthenticatedUser(userId, java.util.List.of("USER")));
        when(userAccountRepository.findByPublicId(userId)).thenReturn(Optional.of(user));
        when(appointmentRepository.findByUserAccountAndIdempotencyKey(user, "idem-001")).thenReturn(Optional.of(existing));

        var response = service.create(
                new CreateAppointmentRequest(slot.getPublicId().toString(), null),
                "idem-001"
        );

        assertEquals(existing.getAppointmentNo(), response.appointmentNo());
        assertEquals(2, slot.getAvailableQuota());
    }

    @Test
    void shouldCancelAppointmentAndReleaseSlotQuota() {
        UUID userId = UUID.randomUUID();
        UserAccountEntity user = buildUser(userId);
        ScheduleSlotEntity slot = buildSlot(0, "FULL");
        AppointmentEntity appointment = AppointmentEntity.confirmed(user, slot, "APT202604220002", "idem-002", null);
        ReflectionTestUtils.setField(appointment, "id", 200L);
        ReflectionTestUtils.setField(appointment, "publicId", UUID.randomUUID());

        when(currentAuthenticatedUserProvider.getRequiredUser()).thenReturn(new AuthenticatedUser(userId, java.util.List.of("USER")));
        when(userAccountRepository.findByPublicId(userId)).thenReturn(Optional.of(user));
        when(appointmentRepository.findByPublicIdForUpdate(appointment.getPublicId())).thenReturn(Optional.of(appointment));
        when(scheduleSlotRepository.findByPublicIdForUpdate(slot.getPublicId())).thenReturn(Optional.of(slot));

        var response = service.cancel(
                appointment.getPublicId().toString(),
                new CancelAppointmentRequest("plan changed")
        );

        assertEquals("CANCELLED", response.status());
        assertEquals(1, slot.getAvailableQuota());
        assertEquals("OPEN", slot.getStatus());
        verify(operationLogRepository).save(any(AppointmentOperationLogEntity.class));
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
        verify(medoraMetricsService).recordAppointmentCommand("CANCEL", "SUCCESS");
    }

    private UserAccountEntity buildUser(UUID userId) {
        UserAccountEntity user = new UserAccountEntity();
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "publicId", userId);
        user.setEmail("phase3@example.com");
        user.setStatus("ACTIVE");
        user.setRegisterSource("WEB");
        return user;
    }

    private ScheduleSlotEntity buildSlot(int availableQuota, String status) {
        DepartmentEntity department = new DepartmentEntity();
        ReflectionTestUtils.setField(department, "id", 20L);
        ReflectionTestUtils.setField(department, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(department, "name", "Respiratory");

        DoctorEntity doctor = new DoctorEntity();
        ReflectionTestUtils.setField(doctor, "id", 30L);
        ReflectionTestUtils.setField(doctor, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(doctor, "department", department);
        ReflectionTestUtils.setField(doctor, "name", "Dr. Li");
        ReflectionTestUtils.setField(doctor, "title", "Chief Physician");

        ScheduleSlotEntity slot = new ScheduleSlotEntity();
        ReflectionTestUtils.setField(slot, "id", 40L);
        ReflectionTestUtils.setField(slot, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(slot, "department", department);
        ReflectionTestUtils.setField(slot, "doctor", doctor);
        ReflectionTestUtils.setField(slot, "scheduleDate", LocalDate.now().plusDays(1));
        ReflectionTestUtils.setField(slot, "periodCode", "AM");
        ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(slot, "endTime", LocalTime.of(12, 0));
        ReflectionTestUtils.setField(slot, "totalQuota", 20);
        ReflectionTestUtils.setField(slot, "availableQuota", availableQuota);
        ReflectionTestUtils.setField(slot, "fee", BigDecimal.valueOf(20));
        ReflectionTestUtils.setField(slot, "status", status);
        return slot;
    }
}

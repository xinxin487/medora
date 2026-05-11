package com.medora.appointment.service;

import com.medora.appointment.api.request.CancelAppointmentRequest;
import com.medora.appointment.api.request.CreateAppointmentRequest;
import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.appointment.domain.AppointmentEntity;
import com.medora.appointment.domain.AppointmentOperationLogEntity;
import com.medora.appointment.repository.AppointmentOperationLogRepository;
import com.medora.appointment.repository.AppointmentRepository;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.exception.BusinessException;
import com.medora.common.web.RequestMetadata;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AppointmentCommandService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentOperationLogRepository operationLogRepository;

    public AppointmentCommandService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                                     UserAccountRepository userAccountRepository,
                                     ScheduleSlotRepository scheduleSlotRepository,
                                     AppointmentRepository appointmentRepository,
                                     AppointmentOperationLogRepository operationLogRepository) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.appointmentRepository = appointmentRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request, String idempotencyKey) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        String normalizedIdempotencyKey = normalizeToNull(idempotencyKey);
        if (StringUtils.hasText(normalizedIdempotencyKey)) {
            var existing = appointmentRepository.findByUserAccountAndIdempotencyKey(userAccount, normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return AppointmentResponseMapper.toResponse(existing.get());
            }
        }

        ScheduleSlotEntity slot = scheduleSlotRepository.findByPublicIdForUpdate(parseUuid(request.slotId(), "slotId"))
                .orElseThrow(() -> new BusinessException("MASTER_SLOT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "schedule slot not found"));

        if (appointmentRepository.existsByUserAccountAndSlotAndStatus(userAccount, slot, "CONFIRMED")) {
            throw new BusinessException("APPOINTMENT_CREATE_DUPLICATED", HttpStatus.CONFLICT,
                    "appointment for this slot already exists");
        }

        try {
            slot.reserveOne();
        } catch (IllegalStateException ex) {
            String code = "OPEN".equals(slot.getStatus()) ? "APPOINTMENT_SLOT_FULL" : "APPOINTMENT_SLOT_CLOSED";
            throw new BusinessException(code, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }

        AppointmentEntity appointment = AppointmentEntity.confirmed(
                userAccount,
                slot,
                generateAppointmentNo(),
                normalizedIdempotencyKey,
                normalizeToNull(request.remark())
        );
        AppointmentEntity saved = appointmentRepository.save(appointment);
        operationLogRepository.save(AppointmentOperationLogEntity.of(
                saved,
                "BOOK",
                userAccount.getId(),
                RequestMetadata.currentRequestId(),
                null,
                saved.getStatus(),
                "{\"source\":\"WEB\"}"
        ));
        return AppointmentResponseMapper.toResponse(saved);
    }

    @Transactional
    public AppointmentCancelResponse cancel(String appointmentId, CancelAppointmentRequest request) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        AppointmentEntity appointment = appointmentRepository.findByPublicIdForUpdate(parseUuid(appointmentId, "appointmentId"))
                .orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "appointment not found"));

        if (!appointment.belongsTo(userAccount.getId())) {
            throw new BusinessException("APPOINTMENT_NOT_OWNER", HttpStatus.FORBIDDEN,
                    "appointment does not belong to current user");
        }
        String beforeStatus = appointment.getStatus();
        try {
            appointment.cancel(normalizeToNull(request.cancelReason()));
        } catch (IllegalStateException ex) {
            throw new BusinessException("APPOINTMENT_CANCEL_NOT_ALLOWED", HttpStatus.UNPROCESSABLE_ENTITY,
                    ex.getMessage());
        }

        ScheduleSlotEntity slot = scheduleSlotRepository.findByPublicIdForUpdate(appointment.getSlot().getPublicId())
                .orElseThrow(() -> new BusinessException("MASTER_SLOT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "schedule slot not found"));
        slot.releaseOne();
        operationLogRepository.save(AppointmentOperationLogEntity.of(
                appointment,
                "CANCEL",
                userAccount.getId(),
                RequestMetadata.currentRequestId(),
                beforeStatus,
                appointment.getStatus(),
                "{\"source\":\"WEB\"}"
        ));
        return AppointmentResponseMapper.toCancelResponse(appointment);
    }

    private UserAccountEntity getCurrentUserAccount() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(authenticatedUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "current user not found"));
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid uuid");
        }
    }

    private String normalizeToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String generateAppointmentNo() {
        return "APT" + LocalDate.now().format(DATE_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}

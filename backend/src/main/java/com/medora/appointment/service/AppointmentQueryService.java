package com.medora.appointment.service;

import com.medora.appointment.api.query.AppointmentListQuery;
import com.medora.appointment.api.response.AppointmentDetailResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.repository.AppointmentRepository;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.api.PageResponse;
import com.medora.common.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AppointmentQueryService {

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentQueryService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                                   UserAccountRepository userAccountRepository,
                                   AppointmentRepository appointmentRepository) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentItem> listMine(AppointmentListQuery query) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        Pageable pageable = PageRequest.of(normalizePageNo(query.pageNo()), normalizePageSize(query.pageSize()));
        var page = appointmentRepository.searchMine(
                userAccount,
                normalizeToNull(query.status()),
                parseDate(query.scheduleDateFrom()),
                parseDate(query.scheduleDateTo()),
                pageable
        );
        return PageResponse.from(page.map(AppointmentResponseMapper::toItem));
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse getMine(String appointmentId) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        return appointmentRepository.findDetail(parseUuid(appointmentId, "appointmentId"), userAccount)
                .map(AppointmentResponseMapper::toDetail)
                .orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "appointment not found"));
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

    private LocalDate parseDate(String value) {
        String normalized = normalizeToNull(value);
        return normalized == null ? null : LocalDate.parse(normalized);
    }

    private String normalizeToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 0 : pageNo - 1;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}

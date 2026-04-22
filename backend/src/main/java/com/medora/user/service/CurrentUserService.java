package com.medora.user.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.domain.UserProfileEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.repository.UserProfileRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.auth.support.UserRoleResolver;
import com.medora.common.exception.BusinessException;
import com.medora.common.util.MaskingUtils;
import com.medora.masterdata.domain.DepartmentEntity;
import com.medora.masterdata.repository.DepartmentRepository;
import com.medora.user.api.request.UpdateCurrentUserRequest;
import com.medora.user.api.response.CurrentUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final DepartmentRepository departmentRepository;

    public CurrentUserService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                              UserAccountRepository userAccountRepository,
                              UserProfileRepository userProfileRepository,
                              DepartmentRepository departmentRepository) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        UserAccountEntity userAccount = getRequiredAccount();
        UserProfileEntity userProfile = getRequiredProfile(userAccount);
        return toResponse(userAccount, userProfile);
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(UpdateCurrentUserRequest request) {
        UserAccountEntity userAccount = getRequiredAccount();
        UserProfileEntity userProfile = getRequiredProfile(userAccount);
        userProfile.setDisplayName(request.displayName().trim());
        userProfile.setDefaultHospitalCode(normalizeToNull(request.defaultHospitalCode()));
        userProfile.setDefaultDepartment(resolveDefaultDepartment(request.defaultDepartmentId()));
        return toResponse(userAccount, userProfile);
    }

    private UserAccountEntity getRequiredAccount() {
        AuthenticatedUser currentUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(currentUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "user not found"));
    }

    private UserProfileEntity getRequiredProfile(UserAccountEntity userAccount) {
        return userProfileRepository.findByUserAccount(userAccount)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "user profile not found"));
    }

    private DepartmentEntity resolveDefaultDepartment(String defaultDepartmentId) {
        String departmentId = normalizeToNull(defaultDepartmentId);
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findByPublicId(UUID.fromString(departmentId))
                .orElseThrow(() -> new BusinessException("MASTER_DEPARTMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "department not found"));
    }

    private CurrentUserResponse toResponse(UserAccountEntity userAccount, UserProfileEntity userProfile) {
        return new CurrentUserResponse(
                userAccount.getPublicId().toString(),
                userProfile.getDisplayName(),
                MaskingUtils.maskMobile(userAccount.getMobile()),
                MaskingUtils.maskEmail(userAccount.getEmail()),
                userProfile.getDefaultHospitalCode(),
                userProfile.getDefaultDepartment() == null
                        ? null
                        : new CurrentUserResponse.DepartmentSimple(
                        userProfile.getDefaultDepartment().getPublicId().toString(),
                        userProfile.getDefaultDepartment().getName()
                ),
                UserRoleResolver.resolveRoles(userAccount)
        );
    }

    private String normalizeToNull(String value) {
        String normalized = value == null ? null : value.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}

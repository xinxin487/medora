package com.medora.user.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.domain.UserProfileEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.repository.UserProfileRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.masterdata.domain.DepartmentEntity;
import com.medora.masterdata.repository.DepartmentRepository;
import com.medora.user.api.request.UpdateCurrentUserRequest;
import com.medora.user.api.response.CurrentUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    private CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private UserAccountRepository userAccountRepository;
    private UserProfileRepository userProfileRepository;
    private DepartmentRepository departmentRepository;
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentAuthenticatedUserProvider = mock(CurrentAuthenticatedUserProvider.class);
        userAccountRepository = mock(UserAccountRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        currentUserService = new CurrentUserService(
                currentAuthenticatedUserProvider,
                userAccountRepository,
                userProfileRepository,
                departmentRepository
        );
    }

    @Test
    void shouldReturnCurrentUser() {
        UUID userId = UUID.randomUUID();
        UserAccountEntity userAccount = buildUserAccount(userId);
        UserProfileEntity userProfile = new UserProfileEntity();
        userProfile.setDisplayName("张三");

        when(currentAuthenticatedUserProvider.getRequiredUser()).thenReturn(new AuthenticatedUser(userId, java.util.List.of("USER")));
        when(userAccountRepository.findByPublicId(userId)).thenReturn(Optional.of(userAccount));
        when(userProfileRepository.findByUserAccount(userAccount)).thenReturn(Optional.of(userProfile));

        CurrentUserResponse response = currentUserService.getCurrentUser();

        assertEquals("张三", response.displayName());
        assertEquals(java.util.List.of("USER"), response.roles());
    }

    @Test
    void shouldUpdateCurrentUserPreferences() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UserAccountEntity userAccount = buildUserAccount(userId);
        UserProfileEntity userProfile = new UserProfileEntity();
        userProfile.setDisplayName("张三");

        DepartmentEntity department = new DepartmentEntity();
        ReflectionTestUtils.setField(department, "publicId", departmentId);
        ReflectionTestUtils.setField(department, "name", "呼吸内科");

        when(currentAuthenticatedUserProvider.getRequiredUser()).thenReturn(new AuthenticatedUser(userId, java.util.List.of("USER")));
        when(userAccountRepository.findByPublicId(userId)).thenReturn(Optional.of(userAccount));
        when(userProfileRepository.findByUserAccount(userAccount)).thenReturn(Optional.of(userProfile));
        when(departmentRepository.findByPublicId(departmentId)).thenReturn(Optional.of(department));

        CurrentUserResponse response = currentUserService.updateCurrentUser(
                new UpdateCurrentUserRequest("李四", "MAIN", departmentId.toString())
        );

        assertEquals("李四", response.displayName());
        assertEquals("MAIN", response.defaultHospitalCode());
        assertEquals("呼吸内科", response.defaultDepartment().name());
    }

    private UserAccountEntity buildUserAccount(UUID userId) {
        UserAccountEntity userAccount = new UserAccountEntity();
        ReflectionTestUtils.setField(userAccount, "publicId", userId);
        userAccount.setMobile("13800138000");
        userAccount.setEmail("demo@example.com");
        userAccount.setRegisterSource("WEB");
        return userAccount;
    }
}

package com.medora.auth.service;

import com.medora.auth.api.request.LoginRequest;
import com.medora.auth.api.request.RegisterRequest;
import com.medora.auth.api.response.LoginResponse;
import com.medora.auth.api.response.RegisterResponse;
import com.medora.auth.domain.RefreshTokenEntity;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.domain.UserProfileEntity;
import com.medora.auth.repository.RefreshTokenRepository;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.repository.UserProfileRepository;
import com.medora.auth.security.JwtProperties;
import com.medora.auth.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserAccountRepository userAccountRepository;
    private UserProfileRepository userProfileRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenTtl(Duration.ofHours(2));
        jwtProperties.setRefreshTokenTtl(Duration.ofDays(14));
        authService = new AuthService(
                userAccountRepository,
                userProfileRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                jwtProperties
        );
    }

    @Test
    void shouldRegisterUserWithProfile() {
        when(userAccountRepository.existsByMobile("13800138000")).thenReturn(false);
        when(userAccountRepository.existsByEmailIgnoreCase("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "publicId", UUID.randomUUID());
            return entity;
        });

        RegisterResponse response = authService.register(
                new RegisterRequest("13800138000", "demo@example.com", "123456", "张三")
        );

        assertNotNull(response.userId());
        assertEquals("张三", response.displayName());

        ArgumentCaptor<UserProfileEntity> profileCaptor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertEquals("张三", profileCaptor.getValue().getDisplayName());
    }

    @Test
    void shouldLoginAndReturnTokens() {
        UserAccountEntity userAccount = new UserAccountEntity();
        ReflectionTestUtils.setField(userAccount, "publicId", UUID.randomUUID());
        userAccount.setMobile("13800138000");
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("ACTIVE");
        userAccount.setRegisterSource("WEB");

        UserProfileEntity userProfile = new UserProfileEntity();
        userProfile.setDisplayName("张三");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        when(userAccountRepository.findByAccount("13800138000")).thenReturn(Optional.of(userAccount));
        when(userProfileRepository.findByUserAccount(userAccount)).thenReturn(Optional.of(userProfile));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userAccount)).thenReturn("access-token");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.login(
                new LoginRequest("13800138000", "123456", "PASSWORD"),
                servletRequest
        );

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertFalse(response.refreshToken().isBlank());
        assertEquals("张三", response.user().displayName());
        assertEquals(java.util.List.of("USER"), response.user().roles());
        assertTrue(response.user().mobileMasked().contains("****"));
        assertEquals("127.0.0.1", userAccount.getLastLoginIp());
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }
}

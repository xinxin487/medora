package com.medora.auth.service;

import com.medora.auth.api.request.LoginRequest;
import com.medora.auth.api.request.RefreshTokenRequest;
import com.medora.auth.api.request.RegisterRequest;
import com.medora.auth.api.response.AuthTokenResponse;
import com.medora.auth.api.response.LoginResponse;
import com.medora.auth.api.response.LogoutResponse;
import com.medora.auth.api.response.RegisterResponse;
import com.medora.auth.domain.RefreshTokenEntity;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.domain.UserProfileEntity;
import com.medora.auth.repository.RefreshTokenRepository;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.repository.UserProfileRepository;
import com.medora.auth.security.JwtProperties;
import com.medora.auth.security.JwtTokenProvider;
import com.medora.auth.support.UserRoleResolver;
import com.medora.common.exception.BusinessException;
import com.medora.common.util.MaskingUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(UserAccountRepository userAccountRepository,
                       UserProfileRepository userProfileRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String mobile = normalizeToNull(request.mobile());
        String email = normalizeEmail(request.email());
        if (!StringUtils.hasText(mobile) && !StringUtils.hasText(email)) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "mobile or email is required");
        }

        if (StringUtils.hasText(mobile) && userAccountRepository.existsByMobile(mobile)) {
            throw new BusinessException("USER_ALREADY_EXISTS", HttpStatus.CONFLICT, "mobile already exists");
        }
        if (StringUtils.hasText(email) && userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("USER_ALREADY_EXISTS", HttpStatus.CONFLICT, "email already exists");
        }

        UserAccountEntity account = new UserAccountEntity();
        account.setUsername(resolveUsername(mobile, email));
        account.setMobile(mobile);
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        account.setStatus("ACTIVE");
        account.setRegisterSource("WEB");
        userAccountRepository.save(account);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserAccount(account);
        profile.setDisplayName(request.displayName().trim());
        userProfileRepository.save(profile);

        return new RegisterResponse(account.getPublicId().toString(), profile.getDisplayName());
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        if (!"PASSWORD".equalsIgnoreCase(request.loginType())) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST, "unsupported login type");
        }

        UserAccountEntity userAccount = userAccountRepository.findByAccount(request.account().trim())
                .orElseThrow(() -> new BusinessException("AUTH_LOGIN_FAILED", HttpStatus.UNAUTHORIZED,
                        "account or password is incorrect"));

        validateActiveUser(userAccount);
        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new BusinessException("AUTH_LOGIN_FAILED", HttpStatus.UNAUTHORIZED,
                    "account or password is incorrect");
        }

        userAccount.markLoggedIn(resolveClientIp(servletRequest));

        UserProfileEntity userProfile = findProfile(userAccount);
        String accessToken = jwtTokenProvider.generateAccessToken(userAccount);
        String refreshToken = issueRefreshToken(userAccount);

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenTtl().toSeconds(),
                "Bearer",
                new LoginResponse.LoggedInUser(
                        userAccount.getPublicId().toString(),
                        userProfile.getDisplayName(),
                        MaskingUtils.maskMobile(userAccount.getMobile()),
                        UserRoleResolver.resolveRoles(userAccount)
                )
        );
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenEntity storedToken = findActiveRefreshToken(request.refreshToken());
        UserAccountEntity userAccount = storedToken.getUserAccount();
        validateActiveUser(userAccount);

        storedToken.markRevoked();
        String accessToken = jwtTokenProvider.generateAccessToken(userAccount);
        String refreshToken = issueRefreshToken(userAccount);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenTtl().toSeconds(),
                "Bearer"
        );
    }

    @Transactional
    public LogoutResponse logout(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(RefreshTokenEntity::markRevoked);
        return new LogoutResponse(true);
    }

    private UserProfileEntity findProfile(UserAccountEntity userAccount) {
        return userProfileRepository.findByUserAccount(userAccount)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "user profile not found"));
    }

    private void validateActiveUser(UserAccountEntity userAccount) {
        if (!"ACTIVE".equalsIgnoreCase(userAccount.getStatus())) {
            throw new BusinessException("USER_STATUS_INVALID", HttpStatus.UNPROCESSABLE_ENTITY,
                    "user status is invalid");
        }
    }

    private RefreshTokenEntity findActiveRefreshToken(String rawRefreshToken) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new BusinessException("AUTH_REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED,
                        "refresh token is invalid"));

        if (!"ACTIVE".equalsIgnoreCase(refreshToken.getStatus())) {
            throw new BusinessException("AUTH_REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED,
                    "refresh token is invalid");
        }

        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshToken.markExpired();
            throw new BusinessException("AUTH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "refresh token expired");
        }

        refreshToken.setLastUsedAt(OffsetDateTime.now());
        return refreshToken;
    }

    private String issueRefreshToken(UserAccountEntity userAccount) {
        String rawToken = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setUserAccount(userAccount);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setStatus("ACTIVE");
        refreshToken.setExpiresAt(OffsetDateTime.now().plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not supported", ex);
        }
    }

    private String resolveClientIp(HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return servletRequest.getRemoteAddr();
    }

    private String resolveUsername(String mobile, String email) {
        if (StringUtils.hasText(email)) {
            return email;
        }
        return mobile;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeToNull(String value) {
        String normalized = value == null ? null : value.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}

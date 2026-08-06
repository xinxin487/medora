package com.medora.auth.security;

import com.medora.auth.domain.UserAccountEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenProviderTest {

    @Test
    void shouldGenerateAndParseAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("medora-test");
        properties.setSecret("medora-test-secret-key-which-is-long-enough-2026");
        properties.setAccessTokenTtl(Duration.ofHours(2));

        JwtTokenProvider provider = new JwtTokenProvider(properties);

        UserAccountEntity userAccount = new UserAccountEntity();
        ReflectionTestUtils.setField(userAccount, "publicId", UUID.randomUUID());
        userAccount.setRegisterSource("WEB");

        String token = provider.generateAccessToken(userAccount);
        AuthenticatedUser authenticatedUser = provider.parse(token);

        assertEquals(userAccount.getPublicId(), authenticatedUser.userPublicId());
        assertEquals(java.util.List.of("USER"), authenticatedUser.roles());
    }
}

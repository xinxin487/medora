package com.medora.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "medora.auth.jwt")
public class JwtProperties {

    private String issuer;
    private String secret;
    private Duration accessTokenTtl = Duration.ofHours(2);
    private Duration refreshTokenTtl = Duration.ofDays(14);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}

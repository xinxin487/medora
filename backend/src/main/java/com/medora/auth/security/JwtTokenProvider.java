package com.medora.auth.security;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.support.UserRoleResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserAccountEntity userAccount) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userAccount.getPublicId().toString())
                .claim("roles", UserRoleResolver.resolveRoles(userAccount))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.getAccessTokenTtl())))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = parseClaims(token).getPayload();
        Object rolesClaim = claims.get("roles");
        List<String> roles = rolesClaim instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of("USER");
        return new AuthenticatedUser(UUID.fromString(claims.getSubject()), roles);
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token);
    }
}

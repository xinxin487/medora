package com.medora.auth.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userPublicId,
        List<String> roles
) {
    public Collection<SimpleGrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}

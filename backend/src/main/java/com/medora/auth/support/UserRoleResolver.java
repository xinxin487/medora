package com.medora.auth.support;

import com.medora.auth.domain.UserAccountEntity;

import java.util.List;

public final class UserRoleResolver {

    private UserRoleResolver() {
    }

    public static List<String> resolveRoles(UserAccountEntity userAccount) {
        if ("ADMIN".equalsIgnoreCase(userAccount.getRegisterSource())) {
            return List.of("ADMIN");
        }
        return List.of("USER");
    }
}

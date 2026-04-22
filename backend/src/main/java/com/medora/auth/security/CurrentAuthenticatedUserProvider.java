package com.medora.auth.security;

import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAuthenticatedUserProvider {

    public AuthenticatedUser getRequiredUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException("AUTH_UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "authentication required");
        }
        return user;
    }
}

package com.medora.a2ui.controller;

import com.medora.a2ui.protocol.A2uiMessage;
import com.medora.a2ui.service.A2uiSurfaceRecoveryService;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.api.ApiResponse;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/a2ui/surfaces")
public class A2uiSurfaceController {
    private final A2uiSurfaceRecoveryService surfaces;
    private final CurrentAuthenticatedUserProvider authenticatedUser;
    private final UserAccountRepository users;

    public A2uiSurfaceController(A2uiSurfaceRecoveryService surfaces,
                                 CurrentAuthenticatedUserProvider authenticatedUser,
                                 UserAccountRepository users) {
        this.surfaces = surfaces;
        this.authenticatedUser = authenticatedUser;
        this.users = users;
    }

    @GetMapping
    public ApiResponse<List<A2uiMessage>> restore(@RequestParam String conversationId) {
        Long userId = users.findByPublicId(authenticatedUser.getRequiredUser().userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "current user not found"))
                .getId();
        try {
            return ApiResponse.success(surfaces.restore(userId, UUID.fromString(conversationId)));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST, "conversationId must be a valid uuid");
        }
    }
}

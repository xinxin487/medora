package com.medora.a2ui.controller;

import com.medora.a2ui.api.A2uiActionRequest;
import com.medora.a2ui.api.A2uiActionResponse;
import com.medora.a2ui.service.A2uiActionService;
import com.medora.a2ui.service.ConfirmedA2uiAction;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.api.ApiResponse;
import com.medora.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/a2ui/actions")
public class A2uiActionController {
    private final A2uiActionService actions;
    private final CurrentAuthenticatedUserProvider authenticatedUser;
    private final UserAccountRepository users;

    public A2uiActionController(A2uiActionService actions, CurrentAuthenticatedUserProvider authenticatedUser,
                                UserAccountRepository users) {
        this.actions = actions; this.authenticatedUser = authenticatedUser; this.users = users;
    }

    @PostMapping
    public ApiResponse<A2uiActionResponse> consume(@Valid @RequestBody A2uiActionRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = users.findByPublicId(authenticatedUser.getRequiredUser().userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "current user not found"))
                .getId();
        try {
            ConfirmedA2uiAction result = actions.confirm(userId, request.actionId(), request.surfaceId(),
                    request.confirmationToken(), idempotencyKey);
            return ApiResponse.success(new A2uiActionResponse(result.actionId(), result.status(), result.appointmentId()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("A2UI_ACTION_INVALID", HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            String code = ex.getMessage() != null && ex.getMessage().contains("expired")
                    ? "A2UI_ACTION_EXPIRED" : "A2UI_ACTION_CONFLICT";
            throw new BusinessException(code, HttpStatus.CONFLICT, ex.getMessage());
        }
    }
}

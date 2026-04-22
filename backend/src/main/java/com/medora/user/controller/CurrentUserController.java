package com.medora.user.controller;

import com.medora.common.api.ApiResponse;
import com.medora.user.api.request.UpdateCurrentUserRequest;
import com.medora.user.api.response.CurrentUserResponse;
import com.medora.user.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<CurrentUserResponse> getCurrentUser() {
        return ApiResponse.success(currentUserService.getCurrentUser());
    }

    @PutMapping
    public ApiResponse<CurrentUserResponse> updateCurrentUser(@Valid @RequestBody UpdateCurrentUserRequest request) {
        return ApiResponse.success(currentUserService.updateCurrentUser(request));
    }
}

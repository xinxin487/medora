package com.medora.auth.controller;

import com.medora.auth.api.request.LoginRequest;
import com.medora.auth.api.request.RefreshTokenRequest;
import com.medora.auth.api.request.RegisterRequest;
import com.medora.auth.api.response.AuthTokenResponse;
import com.medora.auth.api.response.LoginResponse;
import com.medora.auth.api.response.LogoutResponse;
import com.medora.auth.api.response.RegisterResponse;
import com.medora.auth.service.AuthService;
import com.medora.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, servletRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.logout(request));
    }
}

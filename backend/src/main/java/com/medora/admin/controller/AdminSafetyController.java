package com.medora.admin.controller;

import com.medora.admin.service.AdminSafetyQueryService;
import com.medora.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSafetyController {

    private final AdminSafetyQueryService adminSafetyQueryService;

    public AdminSafetyController(AdminSafetyQueryService adminSafetyQueryService) {
        this.adminSafetyQueryService = adminSafetyQueryService;
    }

    @GetMapping("/safety-stats")
    public ApiResponse<AdminSafetyQueryService.SafetyStatsResponse> getSafetyStats() {
        return ApiResponse.success(adminSafetyQueryService.getSafetyStats());
    }
}

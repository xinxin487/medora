package com.medora.admin.controller;

import com.medora.admin.service.AdminSafetyQueryService;
import com.medora.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台 - 安全防护", description = "幻觉防护统计")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSafetyController {

    private final AdminSafetyQueryService adminSafetyQueryService;

    public AdminSafetyController(AdminSafetyQueryService adminSafetyQueryService) {
        this.adminSafetyQueryService = adminSafetyQueryService;
    }

    @Operation(summary = "获取安全防护统计数据")
    @GetMapping("/safety-stats")
    public ApiResponse<AdminSafetyQueryService.SafetyStatsResponse> getSafetyStats() {
        return ApiResponse.success(adminSafetyQueryService.getSafetyStats());
    }
}

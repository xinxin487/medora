package com.medora.admin.controller;

import com.medora.admin.api.response.AdminToolDiagnosticItem;
import com.medora.admin.service.AdminToolDiagnosticsService;
import com.medora.common.api.ApiResponse;
import com.medora.mcp.health.McpRefreshService;
import com.medora.mcp.health.McpHealthService;
import com.medora.mcp.health.McpServerHealth;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tools")
public class AdminToolController {
    private final AdminToolDiagnosticsService service;
    private final McpRefreshService refreshService;
    private final McpHealthService healthService;
    public AdminToolController(AdminToolDiagnosticsService service, McpRefreshService refreshService, McpHealthService healthService) { this.service = service; this.refreshService = refreshService; this.healthService = healthService; }

    @GetMapping
    public ApiResponse<List<AdminToolDiagnosticItem>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/mcp/refresh")
    public ApiResponse<Void> refreshMcp() { refreshService.refresh(); return ApiResponse.success(null); }

    @GetMapping("/mcp/health")
    public ApiResponse<List<McpServerHealth>> mcpHealth() { return ApiResponse.success(healthService.all()); }
}

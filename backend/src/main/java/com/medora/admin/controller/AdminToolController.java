package com.medora.admin.controller;

import com.medora.admin.api.response.AdminToolDiagnosticItem;
import com.medora.admin.service.AdminToolDiagnosticsService;
import com.medora.common.api.ApiResponse;
import com.medora.mcp.health.McpRefreshService;
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
    public AdminToolController(AdminToolDiagnosticsService service, McpRefreshService refreshService) { this.service = service; this.refreshService = refreshService; }

    @GetMapping
    public ApiResponse<List<AdminToolDiagnosticItem>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/mcp/refresh")
    public ApiResponse<Void> refreshMcp() { refreshService.refresh(); return ApiResponse.success(null); }
}

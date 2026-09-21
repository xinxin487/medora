package com.medora.admin.controller;

import com.medora.admin.api.response.AdminToolDiagnosticItem;
import com.medora.admin.service.AdminToolDiagnosticsService;
import com.medora.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tools")
public class AdminToolController {
    private final AdminToolDiagnosticsService service;
    public AdminToolController(AdminToolDiagnosticsService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<AdminToolDiagnosticItem>> list() {
        return ApiResponse.success(service.list());
    }
}

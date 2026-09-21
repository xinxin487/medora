package com.medora.admin.controller;

import com.medora.admin.api.response.AdminContextDiagnosticResponse;
import com.medora.admin.service.AdminContextDiagnosticService;
import com.medora.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/context-diagnostics")
public class AdminContextDiagnosticController {
    private final AdminContextDiagnosticService service;
    public AdminContextDiagnosticController(AdminContextDiagnosticService service) { this.service = service; }
    @GetMapping("/{traceId}")
    public ApiResponse<AdminContextDiagnosticResponse> get(@PathVariable String traceId) { return ApiResponse.success(service.get(traceId)); }
}

package com.medora.admin.controller;

import com.medora.admin.service.AdminRagDiagnosticService;
import com.medora.common.api.ApiResponse;
import com.medora.knowledge.api.request.KnowledgeSearchTestRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rag-diagnostics")
public class AdminRagDiagnosticController {
    private final AdminRagDiagnosticService service;
    public AdminRagDiagnosticController(AdminRagDiagnosticService service) { this.service = service; }

    @PostMapping("/query")
    public ApiResponse<AdminRagDiagnosticService.DiagnosticResponse> query(@Valid @RequestBody KnowledgeSearchTestRequest request) {
        return ApiResponse.success(service.diagnose(request.query(), request.bizScope(), request.topK()));
    }
}

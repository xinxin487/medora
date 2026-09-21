package com.medora.admin.controller;

import com.medora.admin.api.response.AdminToolAuditItem;
import com.medora.admin.service.AdminToolAuditQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tool-audits")
public class AdminToolAuditController {
    private final AdminToolAuditQueryService service;
    public AdminToolAuditController(AdminToolAuditQueryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<AdminToolAuditItem>> list(@RequestParam(required = false) String toolName,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) Integer pageNo,
                                                               @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(service.list(toolName, status, pageNo, pageSize));
    }
}

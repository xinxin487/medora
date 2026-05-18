package com.medora.admin.controller;

import com.medora.admin.api.response.AdminAuditLogItem;
import com.medora.admin.service.AdminAuditLogQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogQueryService adminAuditLogQueryService;

    public AdminAuditLogController(AdminAuditLogQueryService adminAuditLogQueryService) {
        this.adminAuditLogQueryService = adminAuditLogQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAuditLogItem>> list(
            @RequestParam(required = false) String operatorType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminAuditLogQueryService.list(
                operatorType,
                actionType,
                resourceType,
                requestId,
                pageNo,
                pageSize
        ));
    }
}

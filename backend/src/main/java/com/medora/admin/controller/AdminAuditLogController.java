package com.medora.admin.controller;

import com.medora.admin.api.response.AdminAuditLogItem;
import com.medora.admin.service.AdminAuditLogQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台 - 审计日志", description = "操作审计日志查询")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogQueryService adminAuditLogQueryService;

    public AdminAuditLogController(AdminAuditLogQueryService adminAuditLogQueryService) {
        this.adminAuditLogQueryService = adminAuditLogQueryService;
    }

    @Operation(summary = "查询审计日志列表")
    @GetMapping
    public ApiResponse<PageResponse<AdminAuditLogItem>> list(
            @Parameter(description = "操作者类型筛选") @RequestParam(required = false) String operatorType,
            @Parameter(description = "操作类型筛选") @RequestParam(required = false) String actionType,
            @Parameter(description = "资源类型筛选") @RequestParam(required = false) String resourceType,
            @Parameter(description = "请求 ID 精确匹配") @RequestParam(required = false) String requestId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminAuditLogQueryService.list(
                operatorType, actionType, resourceType, requestId, pageNo, pageSize
        ));
    }
}

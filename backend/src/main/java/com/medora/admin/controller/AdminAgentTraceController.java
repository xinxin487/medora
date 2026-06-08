package com.medora.admin.controller;

import com.medora.admin.api.response.AdminAgentTraceDetailResponse;
import com.medora.admin.api.response.AdminAgentTraceItem;
import com.medora.admin.service.AdminTraceQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台 - Agent Trace", description = "Agent 全链路追踪查询")
@RestController
@RequestMapping("/api/v1/admin/agent-traces")
public class AdminAgentTraceController {

    private final AdminTraceQueryService adminTraceQueryService;

    public AdminAgentTraceController(AdminTraceQueryService adminTraceQueryService) {
        this.adminTraceQueryService = adminTraceQueryService;
    }

    @Operation(summary = "查询 Trace 列表")
    @GetMapping
    public ApiResponse<PageResponse<AdminAgentTraceItem>> list(
            @Parameter(description = "Trace ID") @RequestParam(required = false) String traceId,
            @Parameter(description = "会话 ID") @RequestParam(required = false) String conversationId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "路由模式筛选") @RequestParam(required = false) String routeMode,
            @Parameter(description = "意图类型筛选") @RequestParam(required = false) String intentType,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminTraceQueryService.list(
                traceId, conversationId, status, routeMode, intentType, pageNo, pageSize
        ));
    }

    @Operation(summary = "查询 Trace 详情")
    @GetMapping("/{traceId}")
    public ApiResponse<AdminAgentTraceDetailResponse> get(
            @Parameter(description = "Trace ID") @PathVariable String traceId) {
        return ApiResponse.success(adminTraceQueryService.get(traceId));
    }
}

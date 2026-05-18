package com.medora.admin.controller;

import com.medora.admin.api.response.AdminAgentTraceDetailResponse;
import com.medora.admin.api.response.AdminAgentTraceItem;
import com.medora.admin.service.AdminTraceQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agent-traces")
public class AdminAgentTraceController {

    private final AdminTraceQueryService adminTraceQueryService;

    public AdminAgentTraceController(AdminTraceQueryService adminTraceQueryService) {
        this.adminTraceQueryService = adminTraceQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAgentTraceItem>> list(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String routeMode,
            @RequestParam(required = false) String intentType,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminTraceQueryService.list(
                traceId,
                conversationId,
                status,
                routeMode,
                intentType,
                pageNo,
                pageSize
        ));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<AdminAgentTraceDetailResponse> get(@PathVariable String traceId) {
        return ApiResponse.success(adminTraceQueryService.get(traceId));
    }
}

package com.medora.admin.controller;

import com.medora.admin.api.response.AdminChatMessageItem;
import com.medora.admin.api.response.AdminConversationDetailResponse;
import com.medora.admin.api.response.AdminConversationItem;
import com.medora.admin.service.AdminConversationQueryService;
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

@Tag(name = "管理后台 - 会话", description = "管理员查看所有会话与消息")
@RestController
@RequestMapping("/api/v1/admin/conversations")
public class AdminConversationController {

    private final AdminConversationQueryService adminConversationQueryService;

    public AdminConversationController(AdminConversationQueryService adminConversationQueryService) {
        this.adminConversationQueryService = adminConversationQueryService;
    }

    @Operation(summary = "查询所有会话列表")
    @GetMapping
    public ApiResponse<PageResponse<AdminConversationItem>> list(
            @Parameter(description = "关键字搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "场景类型筛选") @RequestParam(required = false) String sceneType,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminConversationQueryService.list(keyword, status, sceneType, pageNo, pageSize));
    }

    @Operation(summary = "查询会话详情")
    @GetMapping("/{conversationId}")
    public ApiResponse<AdminConversationDetailResponse> get(
            @Parameter(description = "会话 ID") @PathVariable String conversationId) {
        return ApiResponse.success(adminConversationQueryService.get(conversationId));
    }

    @Operation(summary = "查询会话的消息记录")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<AdminChatMessageItem>> listMessages(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(adminConversationQueryService.listMessages(conversationId, pageNo, pageSize));
    }
}

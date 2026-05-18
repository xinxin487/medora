package com.medora.admin.controller;

import com.medora.admin.api.response.AdminChatMessageItem;
import com.medora.admin.api.response.AdminConversationDetailResponse;
import com.medora.admin.api.response.AdminConversationItem;
import com.medora.admin.service.AdminConversationQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/conversations")
public class AdminConversationController {

    private final AdminConversationQueryService adminConversationQueryService;

    public AdminConversationController(AdminConversationQueryService adminConversationQueryService) {
        this.adminConversationQueryService = adminConversationQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminConversationItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sceneType,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(adminConversationQueryService.list(keyword, status, sceneType, pageNo, pageSize));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<AdminConversationDetailResponse> get(@PathVariable String conversationId) {
        return ApiResponse.success(adminConversationQueryService.get(conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<AdminChatMessageItem>> listMessages(@PathVariable String conversationId,
                                                                        @RequestParam(required = false) Integer pageNo,
                                                                        @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(adminConversationQueryService.listMessages(conversationId, pageNo, pageSize));
    }
}

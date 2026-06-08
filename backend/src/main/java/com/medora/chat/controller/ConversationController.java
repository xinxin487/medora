package com.medora.chat.controller;

import com.medora.chat.api.request.CreateConversationRequest;
import com.medora.chat.api.request.StopConversationRequest;
import com.medora.chat.api.response.ChatMessageItem;
import com.medora.chat.api.response.ConversationItem;
import com.medora.chat.api.response.ConversationResponse;
import com.medora.chat.api.response.ConversationSummaryResponse;
import com.medora.chat.api.response.StopConversationResponse;
import com.medora.chat.service.ChatRuntimeService;
import com.medora.chat.service.ConversationService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会话管理", description = "创建、查询、停止会话")
@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatRuntimeService chatRuntimeService;

    public ConversationController(ConversationService conversationService,
                                   ChatRuntimeService chatRuntimeService) {
        this.conversationService = conversationService;
        this.chatRuntimeService = chatRuntimeService;
    }

    @Operation(summary = "创建新会话")
    @PostMapping
    public ApiResponse<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success(conversationService.create(request));
    }

    @Operation(summary = "查询当前用户的会话列表")
    @GetMapping
    public ApiResponse<PageResponse<ConversationItem>> listMine(
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(conversationService.listMine(pageNo, pageSize));
    }

    @Operation(summary = "查询会话的消息记录")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<ChatMessageItem>> listMessages(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(conversationService.listMessages(conversationId, pageNo, pageSize));
    }

    @Operation(summary = "查询会话上下文摘要", description = "返回场景类型、摘要文本、业务状态、最近消息")
    @GetMapping("/{conversationId}/summary")
    public ApiResponse<ConversationSummaryResponse> getSummary(
            @Parameter(description = "会话 ID") @PathVariable String conversationId) {
        return ApiResponse.success(conversationService.getSummary(conversationId));
    }

    @Operation(summary = "停止当前正在进行的 SSE 流式回复")
    @PostMapping("/{conversationId}/stop")
    public ApiResponse<StopConversationResponse> stop(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Valid @RequestBody StopConversationRequest request) {
        boolean stopped = chatRuntimeService.stopStream(conversationId);
        return ApiResponse.success(new StopConversationResponse(
                stopped ? "STREAM_STOPPED" : "NO_ACTIVE_STREAM"));
    }
}

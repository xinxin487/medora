package com.medora.chat.controller;

import com.medora.chat.api.request.CreateConversationRequest;
import com.medora.chat.api.request.StopConversationRequest;
import com.medora.chat.api.response.ChatMessageItem;
import com.medora.chat.api.response.ConversationItem;
import com.medora.chat.api.response.ConversationResponse;
import com.medora.chat.api.response.StopConversationResponse;
import com.medora.chat.service.ConversationService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success(conversationService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationItem>> listMine(@RequestParam(required = false) Integer pageNo,
                                                                @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(conversationService.listMine(pageNo, pageSize));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<ChatMessageItem>> listMessages(@PathVariable String conversationId,
                                                                   @RequestParam(required = false) Integer pageNo,
                                                                   @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(conversationService.listMessages(conversationId, pageNo, pageSize));
    }

    @PostMapping("/{conversationId}/stop")
    public ApiResponse<StopConversationResponse> stop(@PathVariable String conversationId,
                                                      @Valid @RequestBody StopConversationRequest request) {
        return ApiResponse.success(new StopConversationResponse("NO_ACTIVE_STREAM"));
    }
}

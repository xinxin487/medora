package com.medora.chat.controller;

import com.medora.chat.api.request.ChatStreamRequest;
import com.medora.chat.service.ChatRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI 对话", description = "SSE 流式对话")
@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ChatStreamController {

    private final ChatRuntimeService chatRuntimeService;

    public ChatStreamController(ChatRuntimeService chatRuntimeService) {
        this.chatRuntimeService = chatRuntimeService;
    }

    @Operation(summary = "发送消息并接收 SSE 流式回复", description = "返回 text/event-stream，包含 message_start/delta/end 事件")
    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Valid @RequestBody ChatStreamRequest request) {
        return chatRuntimeService.stream(conversationId, request);
    }
}

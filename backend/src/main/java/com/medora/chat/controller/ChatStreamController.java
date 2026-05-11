package com.medora.chat.controller;

import com.medora.chat.api.request.ChatStreamRequest;
import com.medora.chat.service.ChatRuntimeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ChatStreamController {

    private final ChatRuntimeService chatRuntimeService;

    public ChatStreamController(ChatRuntimeService chatRuntimeService) {
        this.chatRuntimeService = chatRuntimeService;
    }

    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String conversationId,
                             @Valid @RequestBody ChatStreamRequest request) {
        return chatRuntimeService.stream(conversationId, request);
    }
}

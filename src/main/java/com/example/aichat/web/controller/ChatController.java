package com.example.aichat.web.controller;

import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.chat.service.ChatService;
import com.example.aichat.web.dto.request.ChatStreamRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式聊天 Controller。
 *
 * <p>SSE 端点：{@code POST /api/chat/stream}，返回 {@link SseEmitter}（30 分钟超时）。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody ChatStreamRequest request) {
        SseEmitter emitter = chatService.createEmitter();
        chatService.streamChat(request, user.getId(), emitter);
        return emitter;
    }
}

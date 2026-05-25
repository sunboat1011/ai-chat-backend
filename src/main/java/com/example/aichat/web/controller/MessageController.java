package com.example.aichat.web.controller;

import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.chat.service.MessageService;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.web.dto.response.ApiResponse;
import com.example.aichat.web.dto.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;

    @DeleteMapping("/messages/{id}")
    public ApiResponse<Void> deleteMessage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id) {
        verifyMessageOwnership(id, user.getId());
        messageService.deleteMessage(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/messages/{id}/restore")
    public ApiResponse<MessageResponse> restoreMessage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id) {
        verifyMessageOwnership(id, user.getId());
        return ApiResponse.success(messageService.restoreMessage(id));
    }

    @PostMapping("/conversations/{convId}/messages/{msgId}/regenerate")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ApiResponse<Void> regenerate(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String convId,
            @PathVariable String msgId) {
        return ApiResponse.error("NOT_IMPLEMENTED", "重新生成功能将在 Stage 6 实现");
    }

    private void verifyMessageOwnership(String messageId, Long userId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (conversationMapper.selectByIdAndUserId(message.getConversationId(), userId) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该消息");
        }
    }
}

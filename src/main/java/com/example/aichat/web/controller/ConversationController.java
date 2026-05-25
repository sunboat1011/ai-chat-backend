package com.example.aichat.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.chat.service.ConversationService;
import com.example.aichat.chat.service.MessageService;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.web.dto.request.CreateConversationRequest;
import com.example.aichat.web.dto.request.UpdateConversationRequest;
import com.example.aichat.web.dto.response.ApiResponse;
import com.example.aichat.web.dto.response.ConversationDetailResponse;
import com.example.aichat.web.dto.response.ConversationResponse;
import com.example.aichat.web.dto.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResponse<ConversationResponse>> list(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Conversation> pageParam = new Page<>(page + 1, size);
        var result = conversationMapper.selectByUserIdPage(pageParam, user.getId());
        return ApiResponse.success(PageResponse.from(result)
                .map(conversationService::toResponse));
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success(conversationService.createConversation(request, user.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDetailResponse> detail(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Conversation conversation = conversationMapper.selectByIdAndUserId(id, user.getId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }

        Page<com.example.aichat.chat.entity.Message> messagePage = new Page<>(page + 1, size);
        var messages = messageMapper.selectByConversationIdPage(messagePage, id);

        return ApiResponse.success(ConversationDetailResponse.builder()
                .conversation(conversationService.toResponse(conversation))
                .messages(PageResponse.from(messages)
                        .map(messageService::toResponse))
                .build());
    }

    @PutMapping("/{id}")
    public ApiResponse<ConversationResponse> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id,
            @Valid @RequestBody UpdateConversationRequest request) {
        return ApiResponse.success(conversationService.updateConversation(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id) {
        conversationService.deleteConversation(id, user.getId());
        return ApiResponse.success(null);
    }
}

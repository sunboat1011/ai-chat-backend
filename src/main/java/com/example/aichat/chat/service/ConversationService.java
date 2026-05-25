package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.common.util.IdGenerator;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.CreateConversationRequest;
import com.example.aichat.web.dto.request.UpdateConversationRequest;
import com.example.aichat.web.dto.response.ConversationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final UserSettingsMapper userSettingsMapper;

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, Long userId) {
        UserSettings settings = userSettingsMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "用户设置不存在"));

        String modelId = request.getModelId() != null ? request.getModelId() : settings.getDefaultModelId();
        if (modelId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未配置默认模型");
        }

        Conversation conversation = Conversation.builder()
                .id(IdGenerator.generateConvId())
                .userId(userId)
                .title(request.getTitle() != null ? request.getTitle() : "新对话")
                .systemPrompt(request.getSystemPrompt())
                .modelId(modelId)
                .temperature(request.getTemperature() != null ? request.getTemperature() : settings.getDefaultTemperature())
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : settings.getDefaultMaxTokens())
                .topP(request.getTopP() != null ? request.getTopP() : settings.getDefaultTopP())
                .build();

        conversationMapper.insert(conversation);
        return toResponse(conversation);
    }

    @Transactional
    public ConversationResponse updateConversation(String id, Long userId, UpdateConversationRequest request) {
        Conversation conversation = conversationMapper.selectByIdAndUserId(id, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }

        if (request.getTitle() != null) {
            conversation.setTitle(request.getTitle());
        }
        if (request.getSystemPrompt() != null) {
            conversation.setSystemPrompt(request.getSystemPrompt());
        }

        conversationMapper.updateById(conversation);
        return toResponse(conversation);
    }

    @Transactional
    public void deleteConversation(String id, Long userId) {
        int rows = conversationMapper.softDelete(id, userId, Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
    }

    public ConversationResponse toResponse(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .systemPrompt(conversation.getSystemPrompt())
                .modelId(conversation.getModelId())
                .temperature(conversation.getTemperature())
                .maxTokens(conversation.getMaxTokens())
                .topP(conversation.getTopP())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}

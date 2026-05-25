package com.example.aichat.user.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.MigrateRequest;
import com.example.aichat.web.dto.response.MigrateResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MigrateService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserSettingsMapper userSettingsMapper;

    private static final int MAX_TITLE_LENGTH = 200;

    @Transactional
    public MigrateResultResponse migrate(Long userId, MigrateRequest request) {
        int convCount = 0;
        int msgCount = 0;

        // 1. 更新用户设置（如果前端传了）
        if (request.getSettings() != null) {
            updateSettingsFromMigrate(userId, request.getSettings());
        }

        // 2. 遍历会话
        if (request.getConversations() != null) {
            for (MigrateRequest.MigrateConversation mc : request.getConversations()) {
                Conversation conv = Conversation.builder()
                        .id(mc.getId())
                        .userId(userId)
                        .title(truncate(mc.getTitle(), MAX_TITLE_LENGTH))
                        .systemPrompt(mc.getSystemPrompt())
                        .modelId(mc.getModelId())
                        .temperature(mc.getTemperature())
                        .maxTokens(mc.getMaxTokens())
                        .topP(mc.getTopP())
                        .build();
                if (mc.getCreatedAt() != null) {
                    conv.setCreatedAt(Instant.ofEpochMilli(mc.getCreatedAt()));
                }
                if (mc.getUpdatedAt() != null) {
                    conv.setUpdatedAt(Instant.ofEpochMilli(mc.getUpdatedAt()));
                }
                conversationMapper.insert(conv);
                convCount++;

                // 3. 遍历消息
                if (mc.getMessages() != null) {
                    for (MigrateRequest.MigrateMessage mm : mc.getMessages()) {
                        if (mm.getContent() == null || mm.getContent().isBlank()) {
                            continue; // 空内容消息跳过
                        }
                        Message msg = Message.builder()
                                .id(mm.getId())
                                .conversationId(conv.getId())
                                .role(mm.getRole())
                                .content(mm.getContent())
                                .modelId(mm.getModelId())
                                .status("done")
                                .isDeleted(false)
                                .build();
                        if (mm.getTimestamp() != null) {
                            msg.setCreatedAt(Instant.ofEpochMilli(mm.getTimestamp()));
                        }
                        messageMapper.insert(msg);
                        msgCount++;
                    }
                }
            }
        }

        return MigrateResultResponse.builder()
                .importedConversations(convCount)
                .importedMessages(msgCount)
                .importedModels(0) // 首版不导入模型
                .build();
    }

    private void updateSettingsFromMigrate(Long userId, MigrateRequest.MigrateSettings ms) {
        UserSettings settings = userSettingsMapper.findByUserId(userId).orElse(null);
        if (settings == null) return;

        if (ms.getTheme() != null) settings.setTheme(ms.getTheme());
        if (ms.getAccentColor() != null) settings.setAccentColor(ms.getAccentColor());
        if (ms.getDefaultSystemPrompt() != null) settings.setDefaultSystemPrompt(ms.getDefaultSystemPrompt());
        if (ms.getDefaultTemperature() != null) settings.setDefaultTemperature(ms.getDefaultTemperature());
        if (ms.getDefaultMaxTokens() != null) settings.setDefaultMaxTokens(ms.getDefaultMaxTokens());
        if (ms.getDefaultTopP() != null) settings.setDefaultTopP(ms.getDefaultTopP());
        if (ms.getDefaultModelId() != null) settings.setDefaultModelId(ms.getDefaultModelId());
        if (ms.getLanguage() != null) settings.setLanguage(ms.getLanguage());

        userSettingsMapper.updateById(settings);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}

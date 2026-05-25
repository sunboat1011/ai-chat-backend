package com.example.aichat.user.service;

import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.UpdateSettingsRequest;
import com.example.aichat.web.dto.response.SettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingsMapper userSettingsMapper;

    public SettingsResponse getSettings(Long userId) {
        UserSettings settings = userSettingsMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户设置不存在"));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse updateSettings(Long userId, UpdateSettingsRequest request) {
        UserSettings settings = userSettingsMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户设置不存在"));

        if (request.getTheme() != null) settings.setTheme(request.getTheme());
        if (request.getAccentColor() != null) settings.setAccentColor(request.getAccentColor());
        if (request.getDefaultSystemPrompt() != null) settings.setDefaultSystemPrompt(request.getDefaultSystemPrompt());
        if (request.getDefaultTemperature() != null) settings.setDefaultTemperature(request.getDefaultTemperature());
        if (request.getDefaultMaxTokens() != null) settings.setDefaultMaxTokens(request.getDefaultMaxTokens());
        if (request.getDefaultTopP() != null) settings.setDefaultTopP(request.getDefaultTopP());
        if (request.getDefaultModelId() != null) settings.setDefaultModelId(request.getDefaultModelId());
        if (request.getLanguage() != null) settings.setLanguage(request.getLanguage());

        userSettingsMapper.updateById(settings);
        return toResponse(settings);
    }

    public SettingsResponse toResponse(UserSettings settings) {
        return SettingsResponse.builder()
                .theme(settings.getTheme())
                .accentColor(settings.getAccentColor())
                .defaultSystemPrompt(settings.getDefaultSystemPrompt())
                .defaultTemperature(settings.getDefaultTemperature())
                .defaultMaxTokens(settings.getDefaultMaxTokens())
                .defaultTopP(settings.getDefaultTopP())
                .defaultModelId(settings.getDefaultModelId())
                .language(settings.getLanguage())
                .build();
    }
}

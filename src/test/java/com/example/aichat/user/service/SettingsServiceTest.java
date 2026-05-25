package com.example.aichat.user.service;

import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.UpdateSettingsRequest;
import com.example.aichat.web.dto.response.SettingsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsMapper userSettingsMapper;

    @InjectMocks
    private SettingsService settingsService;

    @Test
    void getSettings_success() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .theme("dark")
                .accentColor("#3b82f6")
                .defaultModelId("gpt-4o")
                .defaultTemperature(new BigDecimal("0.7"))
                .defaultMaxTokens(2048)
                .defaultTopP(new BigDecimal("1.0"))
                .language("zh-CN")
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));

        SettingsResponse response = settingsService.getSettings(1L);

        assertThat(response.getTheme()).isEqualTo("dark");
        assertThat(response.getAccentColor()).isEqualTo("#3b82f6");
        assertThat(response.getDefaultModelId()).isEqualTo("gpt-4o");
        assertThat(response.getDefaultTemperature()).isEqualTo(new BigDecimal("0.7"));
        assertThat(response.getDefaultMaxTokens()).isEqualTo(2048);
        assertThat(response.getDefaultTopP()).isEqualTo(new BigDecimal("1.0"));
        assertThat(response.getLanguage()).isEqualTo("zh-CN");
    }

    @Test
    void getSettings_notFound_throws() {
        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settingsService.getSettings(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void updateSettings_selectiveUpdate() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .theme("light")
                .accentColor("#ff0000")
                .defaultSystemPrompt("Old prompt")
                .defaultTemperature(new BigDecimal("0.5"))
                .defaultMaxTokens(1024)
                .defaultTopP(new BigDecimal("0.9"))
                .defaultModelId("gpt-4o-mini")
                .language("en-US")
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsMapper.updateById(any(UserSettings.class))).thenReturn(1);

        UpdateSettingsRequest request = new UpdateSettingsRequest();
        request.setTheme("dark");
        // Other fields null - should not be updated

        SettingsResponse response = settingsService.updateSettings(1L, request);

        assertThat(response.getTheme()).isEqualTo("dark");
        // Other fields unchanged
        assertThat(response.getAccentColor()).isEqualTo("#ff0000");
        assertThat(response.getDefaultSystemPrompt()).isEqualTo("Old prompt");
        assertThat(response.getDefaultTemperature()).isEqualTo(new BigDecimal("0.5"));
        assertThat(response.getDefaultModelId()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void updateSettings_updateDefaultModelId() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .theme("system")
                .defaultModelId("gpt-4o-mini")
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsMapper.updateById(any(UserSettings.class))).thenReturn(1);

        UpdateSettingsRequest request = new UpdateSettingsRequest();
        request.setDefaultModelId("claude-3-5-sonnet");

        SettingsResponse response = settingsService.updateSettings(1L, request);

        assertThat(response.getDefaultModelId()).isEqualTo("claude-3-5-sonnet");
    }
}

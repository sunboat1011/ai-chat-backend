package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.CreateConversationRequest;
import com.example.aichat.web.dto.request.UpdateConversationRequest;
import com.example.aichat.web.dto.response.ConversationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createConversation_withDefaults_fromUserSettings() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .defaultModelId("gpt-4o-mini")
                .defaultTemperature(new BigDecimal("0.7"))
                .defaultMaxTokens(2048)
                .defaultTopP(new BigDecimal("1.0"))
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        CreateConversationRequest request = new CreateConversationRequest();
        // No modelId, title, temperature, etc. - all from settings

        ConversationResponse response = conversationService.createConversation(request, 1L);

        assertThat(response.getTitle()).isEqualTo("新对话");
        assertThat(response.getModelId()).isEqualTo("gpt-4o-mini");
        assertThat(response.getTemperature()).isEqualTo(new BigDecimal("0.7"));
        assertThat(response.getMaxTokens()).isEqualTo(2048);
        assertThat(response.getTopP()).isEqualTo(new BigDecimal("1.0"));
        assertThat(response.getId()).startsWith("conv_");

        verify(conversationMapper).insert(argThat(conv ->
                conv.getUserId().equals(1L) &&
                conv.getModelId().equals("gpt-4o-mini") &&
                conv.getTitle().equals("新对话")
        ));
    }

    @Test
    void createConversation_withExplicitValues_overridesDefaults() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .defaultModelId("gpt-4o-mini")
                .defaultTemperature(new BigDecimal("0.7"))
                .defaultMaxTokens(2048)
                .defaultTopP(new BigDecimal("1.0"))
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("Custom Title");
        request.setModelId("gpt-4o");
        request.setTemperature(new BigDecimal("0.5"));
        request.setMaxTokens(1024);
        request.setTopP(new BigDecimal("0.9"));
        request.setSystemPrompt("You are a helpful assistant");

        ConversationResponse response = conversationService.createConversation(request, 1L);

        assertThat(response.getTitle()).isEqualTo("Custom Title");
        assertThat(response.getModelId()).isEqualTo("gpt-4o");
        assertThat(response.getTemperature()).isEqualTo(new BigDecimal("0.5"));
        assertThat(response.getMaxTokens()).isEqualTo(1024);
        assertThat(response.getTopP()).isEqualTo(new BigDecimal("0.9"));
        assertThat(response.getSystemPrompt()).isEqualTo("You are a helpful assistant");
    }

    @Test
    void createConversation_noSettings_throwsInternalError() {
        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.empty());

        CreateConversationRequest request = new CreateConversationRequest();

        assertThatThrownBy(() -> conversationService.createConversation(request, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                });
    }

    @Test
    void createConversation_noModelIdAndNoDefaultModel_throws() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .defaultModelId(null)
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(Optional.of(settings));

        CreateConversationRequest request = new CreateConversationRequest();

        assertThatThrownBy(() -> conversationService.createConversation(request, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("未配置默认模型");
                });
    }

    @Test
    void updateConversation_success() {
        Conversation existing = Conversation.builder()
                .id("conv_123")
                .userId(1L)
                .title("Old Title")
                .systemPrompt("Old prompt")
                .modelId("gpt-4o")
                .build();

        when(conversationMapper.selectByIdAndUserId("conv_123", 1L)).thenReturn(existing);
        when(conversationMapper.updateById(any(Conversation.class))).thenReturn(1);

        UpdateConversationRequest request = new UpdateConversationRequest();
        request.setTitle("New Title");

        ConversationResponse response = conversationService.updateConversation("conv_123", 1L, request);

        assertThat(response.getTitle()).isEqualTo("New Title");
        assertThat(response.getSystemPrompt()).isEqualTo("Old prompt"); // unchanged
    }

    @Test
    void updateConversation_notFound_throws() {
        when(conversationMapper.selectByIdAndUserId("conv_123", 1L)).thenReturn(null);

        UpdateConversationRequest request = new UpdateConversationRequest();
        request.setTitle("New Title");

        assertThatThrownBy(() -> conversationService.updateConversation("conv_123", 1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void deleteConversation_success() {
        when(conversationMapper.softDelete(eq("conv_123"), eq(1L), any(Instant.class))).thenReturn(1);

        conversationService.deleteConversation("conv_123", 1L);

        verify(conversationMapper).softDelete(eq("conv_123"), eq(1L), any(Instant.class));
    }

    @Test
    void deleteConversation_notFound_throws() {
        when(conversationMapper.softDelete(eq("conv_123"), eq(1L), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> conversationService.deleteConversation("conv_123", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void toResponse_mapsAllFields() {
        Conversation conv = Conversation.builder()
                .id("conv_123")
                .title("Test")
                .systemPrompt("Prompt")
                .modelId("gpt-4o")
                .temperature(new BigDecimal("0.7"))
                .maxTokens(2048)
                .topP(new BigDecimal("1.0"))
                .build();

        ConversationResponse response = conversationService.toResponse(conv);

        assertThat(response.getId()).isEqualTo("conv_123");
        assertThat(response.getTitle()).isEqualTo("Test");
        assertThat(response.getSystemPrompt()).isEqualTo("Prompt");
        assertThat(response.getModelId()).isEqualTo("gpt-4o");
        assertThat(response.getTemperature()).isEqualTo(new BigDecimal("0.7"));
        assertThat(response.getMaxTokens()).isEqualTo(2048);
        assertThat(response.getTopP()).isEqualTo(new BigDecimal("1.0"));
    }
}

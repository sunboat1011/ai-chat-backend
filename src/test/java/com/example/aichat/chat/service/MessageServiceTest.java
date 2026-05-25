package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.web.dto.response.MessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageService messageService;

    @Test
    void deleteMessage_success() {
        Message msg = Message.builder()
                .id("msg_123")
                .conversationId("conv_123")
                .role("user")
                .content("Hello")
                .build();

        when(messageMapper.selectById("msg_123")).thenReturn(msg);
        when(messageMapper.softDelete(eq("msg_123"), any(Instant.class))).thenReturn(1);

        messageService.deleteMessage("msg_123");

        verify(messageMapper).softDelete(eq("msg_123"), any(Instant.class));
    }

    @Test
    void deleteMessage_notFound_throws() {
        when(messageMapper.selectById("msg_123")).thenReturn(null);

        assertThatThrownBy(() -> messageService.deleteMessage("msg_123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void restoreMessage_withinWindow_success() {
        Message msg = Message.builder()
                .id("msg_123")
                .conversationId("conv_123")
                .role("user")
                .content("Hello")
                .isDeleted(true)
                .deletedAt(Instant.now())
                .build();

        when(messageMapper.restore(eq("msg_123"), any(Instant.class))).thenReturn(1);
        when(messageMapper.selectById("msg_123")).thenReturn(msg);

        MessageResponse response = messageService.restoreMessage("msg_123");

        assertThat(response.getId()).isEqualTo("msg_123");
        assertThat(response.getContent()).isEqualTo("Hello");
    }

    @Test
    void restoreMessage_afterWindow_throws() {
        when(messageMapper.restore(eq("msg_123"), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> messageService.restoreMessage("msg_123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("已超过 5 秒撤销窗口");
                });
    }

    @Test
    void toResponse_mapsAllFields() {
        Message msg = Message.builder()
                .id("msg_123")
                .role("assistant")
                .content("Response")
                .modelId("gpt-4o")
                .build();

        MessageResponse response = messageService.toResponse(msg);

        assertThat(response.getId()).isEqualTo("msg_123");
        assertThat(response.getRole()).isEqualTo("assistant");
        assertThat(response.getContent()).isEqualTo("Response");
        assertThat(response.getModelId()).isEqualTo("gpt-4o");
    }
}

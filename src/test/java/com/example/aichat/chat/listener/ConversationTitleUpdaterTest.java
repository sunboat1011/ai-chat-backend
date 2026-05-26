package com.example.aichat.chat.listener;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.event.ChatMessageCreatedEvent;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationTitleUpdaterTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private ConversationTitleUpdater updater;

    @Test
    void firstUserMessage_updatesTitleToFirst40Chars() {
        // Arrange
        String convId = "conv_test";
        // 41 chars, truncated to 40
        String content = "这是一个超过四十个字符长度的测试消息内容，专门用来验证标题截断逻辑是否正确工作哈哈";

        Conversation conversation = Conversation.builder()
                .id(convId)
                .title("新对话")
                .build();

        when(conversationMapper.selectById(convId)).thenReturn(conversation);
        when(messageMapper.selectByConversationId(convId))
                .thenReturn(List.of(
                        Message.builder().role("user").isDeleted(false).build()));

        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, convId, "msg_1", 1L, "user", content);

        // Act
        updater.onChatMessageCreated(event);

        // Assert
        org.mockito.ArgumentCaptor<Conversation> captor = org.mockito.ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).updateById(captor.capture());
        Conversation updated = captor.getValue();
        assertThat(updated.getTitle()).hasSize(40);
        assertThat(updated.getTitle()).isEqualTo("这是一个超过四十个字符长度的测试消息内容，专门用来验证标题截断逻辑是否正确工作哈");
    }

    @Test
    void nonUserMessage_doesNotUpdateTitle() {
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, "conv_test", "msg_1", 1L, "assistant", "Hello");

        updater.onChatMessageCreated(event);

        verify(conversationMapper, never()).selectById(any());
        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    void titleAlreadyChanged_doesNotUpdate() {
        String convId = "conv_test";
        Conversation conversation = Conversation.builder()
                .id(convId)
                .title("已修改的标题")
                .build();

        when(conversationMapper.selectById(convId)).thenReturn(conversation);

        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, convId, "msg_1", 1L, "user", "Hello");

        updater.onChatMessageCreated(event);

        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    void secondUserMessage_doesNotUpdateTitle() {
        String convId = "conv_test";
        Conversation conversation = Conversation.builder()
                .id(convId)
                .title("新对话")
                .build();

        when(conversationMapper.selectById(convId)).thenReturn(conversation);
        when(messageMapper.selectByConversationId(convId))
                .thenReturn(List.of(
                        Message.builder().role("user").isDeleted(false).build(),
                        Message.builder().role("assistant").isDeleted(false).build(),
                        Message.builder().role("user").isDeleted(false).build()));

        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, convId, "msg_3", 1L, "user", "Second message");

        updater.onChatMessageCreated(event);

        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    void conversationNotFound_doesNothing() {
        String convId = "conv_missing";
        when(conversationMapper.selectById(convId)).thenReturn(null);

        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, convId, "msg_1", 1L, "user", "Hello");

        updater.onChatMessageCreated(event);

        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    void emptyContent_usesDefaultTitle() {
        String convId = "conv_test";
        Conversation conversation = Conversation.builder()
                .id(convId)
                .title("新对话")
                .build();

        when(conversationMapper.selectById(convId)).thenReturn(conversation);
        when(messageMapper.selectByConversationId(convId))
                .thenReturn(List.of(
                        Message.builder().role("user").isDeleted(false).build()));

        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
                this, convId, "msg_1", 1L, "user", "   ");

        updater.onChatMessageCreated(event);

        verify(conversationMapper).updateById(argThat(c ->
                c.getTitle().equals("新对话")));
    }
}

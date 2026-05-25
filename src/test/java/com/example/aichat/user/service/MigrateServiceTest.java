package com.example.aichat.user.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.MigrateRequest;
import com.example.aichat.web.dto.response.MigrateResultResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrateServiceTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;

    @InjectMocks
    private MigrateService migrateService;

    @Test
    void migrate_success() {
        UserSettings settings = UserSettings.builder()
                .userId(1L)
                .theme("system")
                .build();

        when(userSettingsMapper.findByUserId(1L)).thenReturn(java.util.Optional.of(settings));
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
        when(userSettingsMapper.updateById(any(UserSettings.class))).thenReturn(1);

        MigrateRequest request = new MigrateRequest();

        MigrateRequest.MigrateConversation conv = new MigrateRequest.MigrateConversation();
        conv.setId("conv_old1");
        conv.setTitle("Old Conversation");
        conv.setModelId("gpt-4o");
        conv.setTemperature(new BigDecimal("0.7"));
        conv.setCreatedAt(1700000000000L);

        MigrateRequest.MigrateMessage msg1 = new MigrateRequest.MigrateMessage();
        msg1.setId("msg_old1");
        msg1.setRole("user");
        msg1.setContent("Hello");
        msg1.setTimestamp(1700000001000L);

        MigrateRequest.MigrateMessage msg2 = new MigrateRequest.MigrateMessage();
        msg2.setId("msg_old2");
        msg2.setRole("assistant");
        msg2.setContent("Hi there");
        msg2.setTimestamp(1700000002000L);

        conv.setMessages(List.of(msg1, msg2));
        request.setConversations(List.of(conv));

        MigrateRequest.MigrateSettings ms = new MigrateRequest.MigrateSettings();
        ms.setTheme("dark");
        ms.setDefaultModelId("gpt-4o-mini");
        request.setSettings(ms);

        MigrateResultResponse result = migrateService.migrate(1L, request);

        assertThat(result.getImportedConversations()).isEqualTo(1);
        assertThat(result.getImportedMessages()).isEqualTo(2);

        // Verify settings updated
        verify(userSettingsMapper).updateById(argThat(s -> "dark".equals(s.getTheme())));
    }

    @Test
    void migrate_skipsEmptyContentMessages() {
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        MigrateRequest request = new MigrateRequest();

        MigrateRequest.MigrateConversation conv = new MigrateRequest.MigrateConversation();
        conv.setId("conv_empty");
        conv.setTitle("Test");
        conv.setModelId("gpt-4o");

        MigrateRequest.MigrateMessage msg1 = new MigrateRequest.MigrateMessage();
        msg1.setId("msg1");
        msg1.setRole("user");
        msg1.setContent("Valid message");

        MigrateRequest.MigrateMessage msg2 = new MigrateRequest.MigrateMessage();
        msg2.setId("msg2");
        msg2.setRole("user");
        msg2.setContent(""); // Empty

        MigrateRequest.MigrateMessage msg3 = new MigrateRequest.MigrateMessage();
        msg3.setId("msg3");
        msg3.setRole("user");
        msg3.setContent("   "); // Blank

        MigrateRequest.MigrateMessage msg4 = new MigrateRequest.MigrateMessage();
        msg4.setId("msg4");
        msg4.setRole("user");
        msg4.setContent(null); // Null

        conv.setMessages(List.of(msg1, msg2, msg3, msg4));
        request.setConversations(List.of(conv));

        MigrateResultResponse result = migrateService.migrate(1L, request);

        assertThat(result.getImportedConversations()).isEqualTo(1);
        assertThat(result.getImportedMessages()).isEqualTo(1); // Only msg1

        verify(messageMapper, times(1)).insert(any(Message.class));
    }

    @Test
    void migrate_truncatesLongTitle() {
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        MigrateRequest request = new MigrateRequest();

        MigrateRequest.MigrateConversation conv = new MigrateRequest.MigrateConversation();
        conv.setId("conv_long");
        conv.setTitle("a".repeat(250)); // 250 chars, max is 200
        conv.setModelId("gpt-4o");
        request.setConversations(List.of(conv));

        migrateService.migrate(1L, request);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(200);
    }

    @Test
    void migrate_nullConversations_returnsZero() {
        MigrateRequest request = new MigrateRequest();
        request.setConversations(null);

        MigrateResultResponse result = migrateService.migrate(1L, request);

        assertThat(result.getImportedConversations()).isZero();
        assertThat(result.getImportedMessages()).isZero();
    }

    @Test
    void migrate_emptyConversations_returnsZero() {
        MigrateRequest request = new MigrateRequest();
        request.setConversations(List.of());

        MigrateResultResponse result = migrateService.migrate(1L, request);

        assertThat(result.getImportedConversations()).isZero();
        assertThat(result.getImportedMessages()).isZero();
    }
}

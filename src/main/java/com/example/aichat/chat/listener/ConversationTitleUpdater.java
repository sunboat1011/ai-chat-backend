package com.example.aichat.chat.listener;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.event.ChatMessageCreatedEvent;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 会话标题自动更新监听器。
 *
 * <p>当会话收到第一条用户消息时，自动截取消息前 40 字作为会话标题（替换默认的"新对话"）。
 * 异步执行，不阻塞主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTitleUpdater {

    private static final int TITLE_MAX_LENGTH = 40;
    private static final String DEFAULT_TITLE = "新对话";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Async
    @TransactionalEventListener
    public void onChatMessageCreated(ChatMessageCreatedEvent event) {
        if (!"user".equals(event.getRole())) {
            return;
        }

        Conversation conversation = conversationMapper.selectById(event.getConversationId());
        if (conversation == null) {
            return;
        }

        // 只有标题仍为默认值时才更新
        if (!DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }

        // 确认这是该会话第一条用户消息（防止并发重复更新）
        long userMessageCount = messageMapper.selectByConversationId(event.getConversationId())
            .stream()
            .filter(m -> "user".equals(m.getRole()) && Boolean.FALSE.equals(m.getIsDeleted()))
            .count();

        if (userMessageCount > 1) {
            return;
        }

        String newTitle = truncate(event.getContent(), TITLE_MAX_LENGTH);
        conversation.setTitle(newTitle);
        conversationMapper.updateById(conversation);

        log.debug("Auto-updated conversation title: convId={}, title={}",
            event.getConversationId(), newTitle);
    }

    private String truncate(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}

package com.example.aichat.chat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 聊天消息创建领域事件。
 *
 * <p>在 user message 插入完成、以及 assistant message 流式完成后分别发布。
 * 监听器可异步订阅此事件，用于：自动生成会话标题、统计、审计等。
 *
 * <p>为二期 Kafka 化解耦预留的演练——当前用 {@code @Async} 本地异步执行。
 */
@Getter
public class ChatMessageCreatedEvent extends ApplicationEvent {

    private final String conversationId;
    private final String messageId;
    private final Long userId;
    private final String role;
    private final String content;

    public ChatMessageCreatedEvent(Object source, String conversationId, String messageId,
                                   Long userId, String role, String content) {
        super(source);
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.userId = userId;
        this.role = role;
        this.content = content;
    }
}

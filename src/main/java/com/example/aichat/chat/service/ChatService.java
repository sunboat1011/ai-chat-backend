package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.event.ChatMessageCreatedEvent;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.common.util.IdGenerator;
import com.example.aichat.model.adapter.DynamicChatModelFactory;
import com.example.aichat.model.entity.ModelConfig;
import com.example.aichat.model.service.ModelConfigService;
import com.example.aichat.web.dto.request.ChatStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 聊天服务：SSE 流式对话核心。
 *
 * <p>状态机：
 * <pre>
 * 1. 同步事务：插入 user message（status=done）
 * 2. 同步事务：创建 assistant placeholder（status=streaming, content=空）
 * 3. 启动异步 LLM 调用
 *    ├─ 每个 chunk：通过 SseEmitter 推送（不写库）
 *    ├─ 全部完成：UPDATE assistant SET content=全文, status=done
 *    ├─ 客户端断开：UPDATE status=interrupted, content=已接收部分
 *    └─ LLM 报错：UPDATE status=error, content=null
 * 4. 发布 ChatMessageCreatedEvent
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final long SSE_TIMEOUT_MS = 1_800_000L; // 30 分钟

    private final DynamicChatModelFactory modelFactory;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ModelConfigService modelConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${ai.chat.context.max-messages:20}")
    private int maxContextMessages;

    // package-private for test replacement
    ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 流式聊天：在同步事务中插入消息，然后异步推送 SSE。
     */
    @Transactional
    public void streamChat(ChatStreamRequest req, Long userId, SseEmitter emitter) {
        Conversation conversation = verifyConversation(req.getConversationId(), userId);

        String modelId = req.getModelId() != null ? req.getModelId() : conversation.getModelId();
        ModelConfig modelConfig = modelConfigService.getAvailableModel(modelId, userId);

        // 1. 插入 user message
        Message userMessage = Message.builder()
            .id(IdGenerator.generateMessageId())
            .conversationId(conversation.getId())
            .role("user")
            .content(req.getMessage())
            .modelId(modelId)
            .status("done")
            .build();
        messageMapper.insert(userMessage);

        // 2. 创建 assistant placeholder
        String assistantMsgId = IdGenerator.generateMessageId();
        Message placeholder = Message.builder()
            .id(assistantMsgId)
            .conversationId(conversation.getId())
            .role("assistant")
            .content("")
            .modelId(modelId)
            .status("streaming")
            .build();
        messageMapper.insert(placeholder);

        // 3. 发布 user message 事件
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(
            this, conversation.getId(), userMessage.getId(), userId, "user", req.getMessage()));

        // 4. 启动异步 LLM 调用
        AtomicBoolean cancelled = new AtomicBoolean(false);
        setupEmitterCallbacks(emitter, cancelled);

        executor.execute(() -> runStreaming(
            emitter, cancelled, modelConfig, conversation, assistantMsgId,
            req.getSystemPrompt(), req.getTemperature(), req.getMaxTokens(), req.getTopP(), userId));
    }

    /**
     * 重新生成指定 assistant 消息的回复。
     */
    @Transactional
    public void regenerate(String convId, String msgId, Long userId, SseEmitter emitter) {
        Conversation conversation = verifyConversation(convId, userId);

        Message targetMessage = messageMapper.selectById(msgId);
        if (targetMessage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (!"assistant".equals(targetMessage.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能重新生成 assistant 消息");
        }

        String modelId = targetMessage.getModelId() != null
            ? targetMessage.getModelId() : conversation.getModelId();
        ModelConfig modelConfig = modelConfigService.getAvailableModel(modelId, userId);

        // 删除旧的 assistant 消息
        messageMapper.deleteById(msgId);

        // 创建新的 placeholder
        String assistantMsgId = IdGenerator.generateMessageId();
        Message placeholder = Message.builder()
            .id(assistantMsgId)
            .conversationId(convId)
            .role("assistant")
            .content("")
            .modelId(modelId)
            .status("streaming")
            .build();
        messageMapper.insert(placeholder);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        setupEmitterCallbacks(emitter, cancelled);

        executor.execute(() -> runStreaming(
            emitter, cancelled, modelConfig, conversation, assistantMsgId,
            conversation.getSystemPrompt(), conversation.getTemperature(),
            conversation.getMaxTokens(), conversation.getTopP(), userId));
    }

    private void runStreaming(SseEmitter emitter, AtomicBoolean cancelled,
                              ModelConfig modelConfig, Conversation conversation,
                              String assistantMsgId, String overrideSystemPrompt,
                              BigDecimal temperature, Integer maxTokens, BigDecimal topP,
                              Long userId) {
        StringBuilder fullContent = new StringBuilder();
        try {
            ChatModel chatModel = modelFactory.createModel(modelConfig);
            ChatOptions options = modelFactory.buildOptions(modelConfig, temperature, maxTokens, topP);

            List<org.springframework.ai.chat.messages.Message> springAiMessages =
                buildPromptMessages(conversation, overrideSystemPrompt);

            Prompt prompt = new Prompt(springAiMessages, options);

            try (Stream<ChatResponse> stream = chatModel.stream(prompt).toStream()) {
                Iterator<ChatResponse> it = stream.iterator();
                while (it.hasNext()) {
                    if (cancelled.get()) {
                        throw new CancellationException();
                    }
                    ChatResponse response = it.next();
                    String chunk = extractText(response);
                    if (chunk != null && !chunk.isEmpty()) {
                        fullContent.append(chunk);
                        emitter.send(SseEmitter.event().name("message").data(chunk));
                    }
                }
            }

            if (cancelled.get()) {
                throw new CancellationException();
            }

            // 完成
            String finalContent = fullContent.toString();
            messageMapper.updateStatusAndContent(assistantMsgId, "done", finalContent);
            emitter.complete();
            eventPublisher.publishEvent(new ChatMessageCreatedEvent(
                this, conversation.getId(), assistantMsgId, userId, "assistant", finalContent));

        } catch (CancellationException e) {
            log.debug("Streaming cancelled for assistantMsgId={}", assistantMsgId);
            messageMapper.updateStatusAndContent(assistantMsgId, "interrupted", fullContent.toString());
            emitter.complete();
        } catch (Exception e) {
            log.error("Streaming error for assistantMsgId={}", assistantMsgId, e);
            messageMapper.updateStatusAndContent(assistantMsgId, "error", null);
            emitter.completeWithError(e);
        }
    }

    private Conversation verifyConversation(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private List<org.springframework.ai.chat.messages.Message> buildPromptMessages(
            Conversation conversation, String overrideSystemPrompt) {

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // System prompt
        String systemPrompt = overrideSystemPrompt != null
            ? overrideSystemPrompt : conversation.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // 历史消息（截断：最近 N 条）
        List<Message> history = messageMapper.selectByConversationId(conversation.getId());
        List<Message> truncated = truncateHistory(history);

        for (Message msg : truncated) {
            if (Boolean.TRUE.equals(msg.getIsDeleted())) {
                continue;
            }
            String content = msg.getContent() != null ? msg.getContent() : "";
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(content));
                case "assistant" -> messages.add(new AssistantMessage(content));
                default -> log.warn("Unknown message role: {}", msg.getRole());
            }
        }

        return messages;
    }

    private List<Message> truncateHistory(List<Message> history) {
        if (history.size() <= maxContextMessages) {
            return history;
        }
        // 保留最近 maxContextMessages 条
        return history.subList(history.size() - maxContextMessages, history.size());
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null
            || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private void setupEmitterCallbacks(SseEmitter emitter, AtomicBoolean cancelled) {
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
    }

    /**
     * 创建 SseEmitter，30 分钟超时。
     */
    public SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }
}

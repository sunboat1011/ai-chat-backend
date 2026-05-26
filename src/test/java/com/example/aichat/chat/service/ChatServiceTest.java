package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Conversation;
import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.event.ChatMessageCreatedEvent;
import com.example.aichat.chat.mapper.ConversationMapper;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.model.adapter.DynamicChatModelFactory;
import com.example.aichat.model.entity.ModelConfig;
import com.example.aichat.model.service.ModelConfigService;
import com.example.aichat.web.dto.request.ChatStreamRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private DynamicChatModelFactory modelFactory;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ModelConfigService modelConfigService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ChatService chatService;

    // Synchronous executor for testing
    private final AbstractExecutorService syncExecutor = new AbstractExecutorService() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        chatService.executor = syncExecutor;
        ReflectionTestUtils.setField(chatService, "maxContextMessages", 20);
    }

    @Test
    void streamChat_success_statusDone() {
        // Arrange
        String convId = "conv_test123";
        String modelId = "gpt-4o";
        Long userId = 1L;

        ChatStreamRequest req = new ChatStreamRequest();
        req.setConversationId(convId);
        req.setMessage("Hello");
        req.setModelId(modelId);
        req.setTemperature(new BigDecimal("0.7"));

        Conversation conversation = Conversation.builder()
                .id(convId)
                .userId(userId)
                .modelId(modelId)
                .title("Test")
                .build();

        ModelConfig modelConfig = ModelConfig.builder()
                .id(modelId)
                .provider("openai")
                .modelName("gpt-4o")
                .build();

        when(conversationMapper.selectByIdAndUserId(convId, userId)).thenReturn(conversation);
        when(modelConfigService.getAvailableModel(modelId, userId)).thenReturn(modelConfig);
        when(messageMapper.selectByConversationId(convId)).thenReturn(Collections.emptyList());
        when(modelFactory.createModel(modelConfig)).thenReturn(chatModel);
        when(modelFactory.buildOptions(eq(modelConfig), any(), any(), any())).thenReturn(mock(ChatOptions.class));

        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Hi"))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        SseEmitter emitter = chatService.createEmitter();

        // Act
        chatService.streamChat(req, userId, emitter);

        // Assert
        verify(messageMapper, times(2)).insert(any(Message.class));
        verify(messageMapper).updateStatusAndContent(anyString(), eq("done"), eq("Hi"));
        verify(eventPublisher, times(2)).publishEvent(any(ChatMessageCreatedEvent.class));
    }

    @Test
    void streamChat_llmError_statusError() {
        // Arrange
        String convId = "conv_test123";
        String modelId = "gpt-4o";
        Long userId = 1L;

        ChatStreamRequest req = new ChatStreamRequest();
        req.setConversationId(convId);
        req.setMessage("Hello");
        req.setModelId(modelId);

        Conversation conversation = Conversation.builder()
                .id(convId)
                .userId(userId)
                .modelId(modelId)
                .build();

        ModelConfig modelConfig = ModelConfig.builder()
                .id(modelId)
                .provider("openai")
                .modelName("gpt-4o")
                .build();

        when(conversationMapper.selectByIdAndUserId(convId, userId)).thenReturn(conversation);
        when(modelConfigService.getAvailableModel(modelId, userId)).thenReturn(modelConfig);
        when(messageMapper.selectByConversationId(convId)).thenReturn(Collections.emptyList());
        when(modelFactory.createModel(modelConfig)).thenReturn(chatModel);
        when(modelFactory.buildOptions(any(), any(), any(), any())).thenReturn(mock(ChatOptions.class));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("LLM error")));

        SseEmitter emitter = chatService.createEmitter();

        // Act
        chatService.streamChat(req, userId, emitter);

        // Assert
        verify(messageMapper).updateStatusAndContent(anyString(), eq("error"), isNull());
    }

    @Test
    void streamChat_conversationNotFound_throwsNotFound() {
        ChatStreamRequest req = new ChatStreamRequest();
        req.setConversationId("conv_notfound");
        req.setMessage("Hello");
        req.setModelId("gpt-4o");

        when(conversationMapper.selectByIdAndUserId("conv_notfound", 1L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.streamChat(req, 1L, chatService.createEmitter()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void regenerate_success() {
        // Arrange
        String convId = "conv_test123";
        String msgId = "msg_old";
        Long userId = 1L;

        Conversation conversation = Conversation.builder()
                .id(convId)
                .userId(userId)
                .modelId("gpt-4o")
                .build();

        Message targetMessage = Message.builder()
                .id(msgId)
                .conversationId(convId)
                .role("assistant")
                .modelId("gpt-4o")
                .build();

        ModelConfig modelConfig = ModelConfig.builder()
                .id("gpt-4o")
                .provider("openai")
                .modelName("gpt-4o")
                .build();

        when(conversationMapper.selectByIdAndUserId(convId, userId)).thenReturn(conversation);
        when(messageMapper.selectById(msgId)).thenReturn(targetMessage);
        when(modelConfigService.getAvailableModel("gpt-4o", userId)).thenReturn(modelConfig);
        when(messageMapper.selectByConversationId(convId)).thenReturn(Collections.emptyList());
        when(modelFactory.createModel(modelConfig)).thenReturn(chatModel);
        when(modelFactory.buildOptions(any(), any(), any(), any())).thenReturn(mock(ChatOptions.class));

        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Regenerated"))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        SseEmitter emitter = chatService.createEmitter();

        // Act
        chatService.regenerate(convId, msgId, userId, emitter);

        // Assert
        verify(messageMapper).deleteById(msgId);
        verify(messageMapper, times(1)).insert(any(Message.class)); // new placeholder only
        verify(messageMapper).updateStatusAndContent(anyString(), eq("done"), eq("Regenerated"));
    }

    @Test
    void regenerate_notAssistantMessage_throwsParamError() {
        String convId = "conv_test123";
        String msgId = "msg_user";
        Long userId = 1L;

        Conversation conversation = Conversation.builder()
                .id(convId)
                .userId(userId)
                .build();

        Message userMessage = Message.builder()
                .id(msgId)
                .conversationId(convId)
                .role("user")
                .build();

        when(conversationMapper.selectByIdAndUserId(convId, userId)).thenReturn(conversation);
        when(messageMapper.selectById(msgId)).thenReturn(userMessage);

        assertThatThrownBy(() -> chatService.regenerate(convId, msgId, userId, chatService.createEmitter()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void buildPromptMessages_truncatesHistory() {
        // Arrange
        String convId = "conv_test123";
        Conversation conversation = Conversation.builder()
                .id(convId)
                .modelId("gpt-4o")
                .build();

        // Create 25 messages (> maxContextMessages=20)
        List<Message> history = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            history.add(Message.builder()
                    .conversationId(convId)
                    .role(i % 2 == 0 ? "user" : "assistant")
                    .content("msg" + i)
                    .isDeleted(false)
                    .build());
        }

        when(messageMapper.selectByConversationId(convId)).thenReturn(history);
        when(conversationMapper.selectByIdAndUserId(convId, 1L)).thenReturn(conversation);
        when(modelConfigService.getAvailableModel(any(), any())).thenReturn(
                ModelConfig.builder().id("gpt-4o").provider("openai").modelName("gpt-4o").build());
        when(modelFactory.createModel(any())).thenReturn(chatModel);
        when(modelFactory.buildOptions(any(), any(), any(), any())).thenReturn(mock(ChatOptions.class));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))))));

        ChatStreamRequest req = new ChatStreamRequest();
        req.setConversationId(convId);
        req.setMessage("Hello");
        req.setModelId("gpt-4o");

        // Act
        chatService.streamChat(req, 1L, chatService.createEmitter());

        // Assert - verify the prompt was built with truncated messages
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        // 20 history messages (truncated from 25), no system prompt
        assertThat(capturedPrompt.getInstructions()).hasSize(20);
    }
}

package com.example.aichat.model.adapter;

import com.example.aichat.common.util.ApiKeyEncryptor;
import com.example.aichat.model.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 动态 ChatModel 工厂。
 *
 * <p>
 * 根据 {@link ModelConfig} 动态构造对应 provider 的 {@link ChatModel} 实例。
 * 所有 LLM 调用必须经由本工厂，禁止任何 Service 直接 {@code new OpenAiChatModel(...)}。
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/1.0/api/chatclient.html">Spring AI ChatClient</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicChatModelFactory {

    private final ApiKeyEncryptor encryptor;

    /**
     * 根据 ModelConfig 动态构造 ChatModel 实例。
     *
     * @param config 模型配置
     * @return 对应 provider 的 ChatModel
     */
    public ChatModel createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.debug("Creating ChatModel for provider={}, model={}", provider, config.getModelName());

        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiModel(config);
            case "ollama" -> createOllamaModel(config);
            case "zhipuai", "custom" -> createOpenAiCompatibleModel(config);
            case "anthropic" -> throw new UnsupportedOperationException(
                "Anthropic provider not yet supported in this version. " +
                "Please use a custom OpenAI-compatible endpoint instead.");
            default -> throw new IllegalArgumentException("不支持的模型提供商: " + provider);
        };
    }

    /**
     * 根据运行时参数构建对应 provider 的 ChatOptions。
     */
    public ChatOptions buildOptions(ModelConfig config, BigDecimal temperature,
                                    Integer maxTokens, BigDecimal topP) {
        String provider = config.getProvider().toLowerCase();
        return switch (provider) {
            case "openai", "zhipuai", "custom" -> buildOpenAiOptions(config, temperature, maxTokens, topP);
            case "ollama" -> buildOllamaOptions(config, temperature, maxTokens, topP);
            default -> throw new IllegalArgumentException("不支持的模型提供商: " + provider);
        };
    }

    private ChatModel createOpenAiModel(ModelConfig config) {
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(config.getApiBaseUrl())
            .apiKey(decryptKey(config.getApiKey()))
            .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(config.getModelName())
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
    }

    private ChatModel createOpenAiCompatibleModel(ModelConfig config) {
        // 智谱 AI、Custom 等 OpenAI 兼容接口统一走 OpenAiChatModel
        return createOpenAiModel(config);
    }

    private ChatModel createOllamaModel(ModelConfig config) {
        OllamaApi api = OllamaApi.builder()
            .baseUrl(config.getApiBaseUrl())
            .build();

        OllamaOptions options = OllamaOptions.builder()
            .model(config.getModelName())
            .build();

        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(options)
            .build();
    }

    private ChatOptions buildOpenAiOptions(ModelConfig config, BigDecimal temperature,
                                           Integer maxTokens, BigDecimal topP) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
            .model(config.getModelName());
        if (temperature != null) {
            builder.temperature(temperature.doubleValue());
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP.doubleValue());
        }
        return builder.build();
    }

    private ChatOptions buildOllamaOptions(ModelConfig config, BigDecimal temperature,
                                           Integer maxTokens, BigDecimal topP) {
        OllamaOptions options = OllamaOptions.builder()
            .model(config.getModelName())
            .build();
        if (temperature != null) {
            options.setTemperature(temperature.doubleValue());
        }
        if (maxTokens != null) {
            options.setMaxTokens(maxTokens);
        }
        if (topP != null) {
            options.setTopP(topP.doubleValue());
        }
        return options;
    }

    String decryptKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isBlank()) {
            return null;
        }
        return encryptor.decrypt(encryptedKey);
    }
}

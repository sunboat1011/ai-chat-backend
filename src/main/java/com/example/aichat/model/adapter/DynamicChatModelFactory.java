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
        String baseUrl = normalizeBaseUrl(config.getApiBaseUrl());
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(baseUrl)
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
        String baseUrl = normalizeBaseUrl(config.getApiBaseUrl());
        String apiKey = decryptKey(config.getApiKey());

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
            .baseUrl(baseUrl);

        if ("custom".equalsIgnoreCase(config.getProvider()) && apiKey != null && !apiKey.isBlank()) {
            // MiMo 等 Custom provider 使用 api-key header 而非 Authorization: Bearer
            // OpenAiApi.Builder 强制要求 apiKey 非空，先传 dummy 满足校验，
            // 再通过 interceptor/filter 把 Authorization 替换成 api-key
            org.springframework.web.client.RestClient.Builder restClientBuilder =
                org.springframework.web.client.RestClient.builder()
                    .requestInterceptor((request, body, execution) -> {
                        request.getHeaders().remove("Authorization");
                        request.getHeaders().add("api-key", apiKey);
                        return execution.execute(request, body);
                    });
            org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder =
                org.springframework.web.reactive.function.client.WebClient.builder()
                    .filter((request, next) -> {
                        org.springframework.web.reactive.function.client.ClientRequest newRequest =
                            org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                .headers(headers -> {
                                    headers.remove("Authorization");
                                    headers.add("api-key", apiKey);
                                })
                                .build();
                        return next.exchange(newRequest);
                    });
            apiBuilder.apiKey("dummy")
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder);
        } else {
            // 智谱 AI 等标准 OpenAI-compatible 使用 Authorization: Bearer
            apiBuilder.apiKey(apiKey);
        }

        OpenAiApi api = apiBuilder.build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(config.getModelName())
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
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

    /**
     * 规范化 OpenAI-compatible 的 base URL。
     * 如果用户填了带 /v1 后缀的地址，自动去掉，避免 Spring AI 内部再追加一层 /v1。
     */
    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        // 去掉末尾的 /v1（不区分大小写，支持 /v1/ 和 /v1）
        if (trimmed.toLowerCase().endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }
        if (trimmed.toLowerCase().endsWith("/v1/")) {
            return trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed;
    }
}

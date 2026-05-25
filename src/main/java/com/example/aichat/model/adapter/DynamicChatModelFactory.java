package com.example.aichat.model.adapter;

import com.example.aichat.common.util.ApiKeyEncryptor;
import com.example.aichat.model.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 动态 ChatModel 工厂。
 * <p>
 * Stage 4 仅保留骨架：定义 {@link #createModel(ModelConfig)} 方法签名与 provider switch，
 * 所有分支当前抛出 {@link UnsupportedOperationException}。Stage 6 引入 Spring AI 依赖后填充实现。
 *
 * @see <a href="https://github.com/spring-projects/spring-ai">Spring AI</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicChatModelFactory {

    private final ApiKeyEncryptor encryptor;

    /**
     * 根据 ModelConfig 动态构造 ChatModel 实例。
     * <p>
     * <b>Stage 4</b>：返回 {@code Object} 占位，实际实现待 Stage 6 完成（返回类型将改为
     * {@code org.springframework.ai.chat.model.ChatModel}）。
     *
     * @param config 模型配置
     * @return 当前始终抛出异常
     */
    public Object createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.debug("Creating ChatModel for provider={}, model={}", provider, config.getModelName());

        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiModel(config);
            case "anthropic" -> createAnthropicModel(config);
            case "ollama" -> createOllamaModel(config);
            case "zhipuai" -> createZhipuModel(config);
            case "custom" -> createOpenAiCompatibleModel(config);
            default -> throw new IllegalArgumentException("不支持的模型提供商: " + provider);
        };
    }

    private Object createOpenAiModel(ModelConfig config) {
        throw new UnsupportedOperationException("filled in Stage 6");
    }

    private Object createAnthropicModel(ModelConfig config) {
        throw new UnsupportedOperationException("filled in Stage 6");
    }

    private Object createOllamaModel(ModelConfig config) {
        throw new UnsupportedOperationException("filled in Stage 6");
    }

    private Object createZhipuModel(ModelConfig config) {
        throw new UnsupportedOperationException("filled in Stage 6");
    }

    private Object createOpenAiCompatibleModel(ModelConfig config) {
        throw new UnsupportedOperationException("filled in Stage 6");
    }

    String decryptKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isBlank()) {
            return null;
        }
        return encryptor.decrypt(encryptedKey);
    }
}

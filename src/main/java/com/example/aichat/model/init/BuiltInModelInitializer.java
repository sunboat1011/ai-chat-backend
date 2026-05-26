package com.example.aichat.model.init;

import com.example.aichat.common.util.ApiKeyEncryptor;
import com.example.aichat.model.entity.ModelConfig;
import com.example.aichat.model.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class BuiltInModelInitializer implements ApplicationRunner {

    private final ModelConfigMapper modelConfigMapper;
    private final ApiKeyEncryptor encryptor;

    @Value("${ai.builtin-models.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.builtin-models.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${ai.builtin-models.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Initializing built-in models...");

        List<BuiltInModelDef> builtInModels = List.of(
            new BuiltInModelDef("gpt-4o", "GPT-4o", "openai",
                "https://api.openai.com", "gpt-4o", openaiApiKey),
            new BuiltInModelDef("gpt-4o-mini", "GPT-4o Mini", "openai",
                "https://api.openai.com", "gpt-4o-mini", openaiApiKey),
            new BuiltInModelDef("claude-3-5-sonnet", "Claude 3.5 Sonnet", "anthropic",
                "https://api.anthropic.com", "claude-3-5-sonnet-20241022", anthropicApiKey),
            new BuiltInModelDef("deepseek-chat", "DeepSeek Chat", "openai",
                "https://api.deepseek.com", "deepseek-chat", null),
            new BuiltInModelDef("ollama-llama3", "Llama 3 (Local)", "ollama",
                ollamaBaseUrl, "llama3", null)
        );

        for (BuiltInModelDef def : builtInModels) {
            ModelConfig existing = modelConfigMapper.selectById(def.id());
            if (existing != null) {
                updateExisting(existing, def);
            } else {
                insertNew(def);
            }
        }

        log.info("Built-in models initialized: {}", builtInModels.size());
    }

    private void updateExisting(ModelConfig existing, BuiltInModelDef def) {
        boolean hasKey = def.apiKey() != null && !def.apiKey().isBlank();
        boolean isEnabled = hasKey;

        // 仅更新 api_key（加密后）和 is_enabled，不覆盖用户可能改过的 display_name
        if (hasKey) {
            existing.setApiKey(encryptor.encrypt(def.apiKey()));
        }
        existing.setIsEnabled(isEnabled);
        existing.setApiBaseUrl(def.apiBaseUrl());
        existing.setModelName(def.modelName());
        modelConfigMapper.updateById(existing);
    }

    private void insertNew(BuiltInModelDef def) {
        boolean hasKey = def.apiKey() != null && !def.apiKey().isBlank();
        boolean isEnabled = hasKey;

        ModelConfig config = ModelConfig.builder()
            .id(def.id())
            .userId(null)
            .displayName(def.displayName())
            .provider(def.provider())
            .apiBaseUrl(def.apiBaseUrl())
            .apiKey(hasKey ? encryptor.encrypt(def.apiKey()) : null)
            .modelName(def.modelName())
            .isBuiltin(true)
            .isEnabled(isEnabled)
            .build();
        modelConfigMapper.insert(config);
    }

    private record BuiltInModelDef(
        String id, String displayName, String provider,
        String apiBaseUrl, String modelName, String apiKey
    ) {}
}

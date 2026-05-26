package com.example.aichat.model.service;

import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.common.util.ApiKeyEncryptor;
import com.example.aichat.common.util.IdGenerator;
import com.example.aichat.model.entity.ModelConfig;
import com.example.aichat.model.mapper.ModelConfigMapper;
import com.example.aichat.web.dto.request.CreateCustomModelRequest;
import com.example.aichat.web.dto.request.UpdateCustomModelRequest;
import com.example.aichat.web.dto.response.ModelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final ApiKeyEncryptor encryptor;

    public List<ModelResponse> getAvailableModels(Long userId) {
        return modelConfigMapper.selectAllAvailableByUserId(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ModelConfig getAvailableModel(String modelId, Long userId) {
        return modelConfigMapper.selectAllAvailableByUserId(userId).stream()
            .filter(m -> m.getId().equals(modelId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND, "模型不存在或不可用"));
    }

    @Transactional
    public ModelResponse createCustomModel(CreateCustomModelRequest request, Long userId) {
        String modelId = request.getModelId() != null && !request.getModelId().isBlank()
            ? request.getModelId() : IdGenerator.generate("custom");

        if (modelConfigMapper.selectById(modelId) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型ID已存在: " + modelId);
        }

        ModelConfig config = ModelConfig.builder()
            .id(modelId)
            .userId(userId)
            .displayName(request.getDisplayName())
            .provider(request.getProvider())
            .apiBaseUrl(request.getApiBaseUrl())
            .apiKey(request.getApiKey() != null && !request.getApiKey().isBlank()
                ? encryptor.encrypt(request.getApiKey()) : null)
            .modelName(request.getModelName() != null ? request.getModelName() : modelId)
            .isBuiltin(false)
            .isEnabled(true)
            .build();

        modelConfigMapper.insert(config);
        return toResponse(config);
    }

    @Transactional
    public ModelResponse updateCustomModel(String modelId, UpdateCustomModelRequest request, Long userId) {
        ModelConfig config = modelConfigMapper.selectByIdAndUserId(modelId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND, "模型不存在"));

        config.setDisplayName(request.getDisplayName());
        config.setProvider(request.getProvider());
        config.setApiBaseUrl(request.getApiBaseUrl());
        config.setModelName(request.getModelName());

        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            config.setApiKey(encryptor.encrypt(request.getApiKey()));
        }

        modelConfigMapper.updateById(config);
        return toResponse(config);
    }

    @Transactional
    public void deleteCustomModel(String modelId, Long userId) {
        ModelConfig config = modelConfigMapper.selectById(modelId);
        if (config == null) {
            throw new BusinessException(ErrorCode.MODEL_NOT_FOUND, "模型不存在");
        }

        if (Boolean.TRUE.equals(config.getIsBuiltin())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许删除内置模型");
        }

        if (!userId.equals(config.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问");
        }

        modelConfigMapper.deleteById(config.getId());
    }

    /**
     * 查询第一个启用的内置模型 ID，用于注册时初始化用户默认设置。
     */
    public String getFirstEnabledBuiltInModelId() {
        List<ModelConfig> builtIns = modelConfigMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ModelConfig>()
                .eq("is_builtin", true)
                .eq("is_enabled", true)
                .orderByAsc("created_at")
                .last("LIMIT 1"));
        return builtIns.isEmpty() ? null : builtIns.get(0).getId();
    }

    private ModelResponse toResponse(ModelConfig config) {
        return ModelResponse.builder()
            .id(config.getId())
            .displayName(config.getDisplayName())
            .provider(config.getProvider())
            .apiBaseUrl(config.getIsBuiltin() != null && config.getIsBuiltin() ? null : config.getApiBaseUrl())
            .modelName(config.getModelName())
            .isBuiltin(config.getIsBuiltin())
            .isCustom(config.getIsBuiltin() != null ? !config.getIsBuiltin() : null)
            .isEnabled(config.getIsEnabled())
            .build();
    }
}

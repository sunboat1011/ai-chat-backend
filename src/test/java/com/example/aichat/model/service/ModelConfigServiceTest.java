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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigServiceTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private ApiKeyEncryptor encryptor;

    @InjectMocks
    private ModelConfigService modelConfigService;

    @Test
    void getAvailableModels_returnsDesensitizedResponse() {
        ModelConfig builtIn = ModelConfig.builder()
            .id("gpt-4o")
            .displayName("GPT-4o")
            .provider("openai")
            .apiBaseUrl("https://api.openai.com")
            .apiKey("encrypted-key")
            .modelName("gpt-4o")
            .isBuiltin(true)
            .isEnabled(true)
            .build();

        ModelConfig custom = ModelConfig.builder()
            .id("custom_xxx")
            .userId(1L)
            .displayName("My Model")
            .provider("openai")
            .apiBaseUrl("https://api.example.com")
            .apiKey("encrypted-key-2")
            .modelName("gpt-4")
            .isBuiltin(false)
            .isEnabled(true)
            .build();

        when(modelConfigMapper.selectAllAvailableByUserId(1L))
            .thenReturn(List.of(builtIn, custom));

        List<ModelResponse> result = modelConfigService.getAvailableModels(1L);

        assertThat(result).hasSize(2);

        // 内置模型：apiBaseUrl 返回 null
        ModelResponse builtInResp = result.get(0);
        assertThat(builtInResp.getId()).isEqualTo("gpt-4o");
        assertThat(builtInResp.getApiBaseUrl()).isNull();
        assertThat(builtInResp.getIsBuiltin()).isTrue();
        assertThat(builtInResp.getIsCustom()).isFalse();

        // 自定义模型：apiBaseUrl 返回实际值
        ModelResponse customResp = result.get(1);
        assertThat(customResp.getId()).isEqualTo("custom_xxx");
        assertThat(customResp.getApiBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(customResp.getIsBuiltin()).isFalse();
        assertThat(customResp.getIsCustom()).isTrue();

        // 两者都不包含 apiKey（ModelResponse 无此字段，通过反射验证字段不存在）
        assertThat(builtInResp).extracting(resp -> {
            try {
                return resp.getClass().getDeclaredField("apiKey");
            } catch (NoSuchFieldException e) {
                return null;
            }
        }).isNull();
    }

    @Test
    void createCustomModel_usesFrontendModelId() {
        CreateCustomModelRequest request = new CreateCustomModelRequest();
        request.setDisplayName("My GPT");
        request.setModelId("my-gpt-4o");
        request.setApiBaseUrl("https://api.example.com");
        request.setApiKey("sk-test");
        request.setModelName("gpt-4o");
        request.setProvider("openai");

        when(encryptor.encrypt("sk-test")).thenReturn("encrypted-sk-test");
        when(modelConfigMapper.selectById("my-gpt-4o")).thenReturn(null);
        when(modelConfigMapper.insert(any(ModelConfig.class))).thenReturn(1);

        ModelResponse response = modelConfigService.createCustomModel(request, 1L);

        assertThat(response.getId()).isEqualTo("my-gpt-4o");
        assertThat(response.getDisplayName()).isEqualTo("My GPT");
        assertThat(response.getIsBuiltin()).isFalse();
        assertThat(response.getIsCustom()).isTrue();

        verify(modelConfigMapper).insert(argThat(config ->
            config.getId().equals("my-gpt-4o") &&
            config.getUserId().equals(1L) &&
            config.getApiKey().equals("encrypted-sk-test") &&
            Boolean.FALSE.equals(config.getIsBuiltin())
        ));
    }

    @Test
    void createCustomModel_withoutApiKey_allowsNullKey() {
        CreateCustomModelRequest request = new CreateCustomModelRequest();
        request.setDisplayName("Local Model");
        request.setModelId("llama3");
        request.setApiBaseUrl("http://localhost:11434");
        request.setModelName("llama3");
        request.setProvider("ollama");

        when(modelConfigMapper.insert(any(ModelConfig.class))).thenReturn(1);

        ModelResponse response = modelConfigService.createCustomModel(request, 1L);

        verify(modelConfigMapper).insert(argThat(config -> config.getApiKey() == null));
    }

    @Test
    void createCustomModel_generatesId_whenModelIdBlank() {
        CreateCustomModelRequest request = new CreateCustomModelRequest();
        request.setDisplayName("Test");
        request.setModelId("");              // 前端传空
        request.setApiBaseUrl("https://api.example.com");
        request.setModelName("my-gpt-4");
        request.setProvider("openai");

        when(modelConfigMapper.insert(any(ModelConfig.class))).thenReturn(1);

        ModelResponse response = modelConfigService.createCustomModel(request, 1L);

        // 数据库 id 由后端生成
        assertThat(response.getId()).startsWith("custom_");
    }

    @Test
    void updateCustomModel_success() {
        ModelConfig existing = ModelConfig.builder()
            .id("custom_xxx")
            .userId(1L)
            .displayName("Old Name")
            .provider("openai")
            .apiBaseUrl("https://old.com")
            .apiKey("old-key")
            .modelName("gpt-3")
            .isBuiltin(false)
            .isEnabled(true)
            .build();

        UpdateCustomModelRequest request = new UpdateCustomModelRequest();
        request.setDisplayName("New Name");
        request.setApiBaseUrl("https://new.com");
        request.setApiKey("sk-new");
        request.setModelName("gpt-4");
        request.setProvider("openai");

        when(modelConfigMapper.selectByIdAndUserId("custom_xxx", 1L))
            .thenReturn(Optional.of(existing));
        when(encryptor.encrypt("sk-new")).thenReturn("encrypted-new");
        when(modelConfigMapper.updateById(any(ModelConfig.class))).thenReturn(1);

        ModelResponse response = modelConfigService.updateCustomModel("custom_xxx", request, 1L);

        assertThat(response.getDisplayName()).isEqualTo("New Name");
        assertThat(response.getApiBaseUrl()).isEqualTo("https://new.com");
        assertThat(existing.getApiKey()).isEqualTo("encrypted-new");
    }

    @Test
    void updateCustomModel_modelNotFound_throws() {
        when(modelConfigMapper.selectByIdAndUserId("custom_xxx", 1L))
            .thenReturn(Optional.empty());

        UpdateCustomModelRequest request = new UpdateCustomModelRequest();
        request.setDisplayName("New");
        request.setApiBaseUrl("https://new.com");
        request.setModelName("gpt-4");
        request.setProvider("openai");

        assertThatThrownBy(() -> modelConfigService.updateCustomModel("custom_xxx", request, 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MODEL_NOT_FOUND);
            });
    }

    @Test
    void deleteCustomModel_success() {
        ModelConfig config = ModelConfig.builder()
            .id("custom_xxx")
            .userId(1L)
            .isBuiltin(false)
            .build();

        when(modelConfigMapper.selectById("custom_xxx")).thenReturn(config);

        modelConfigService.deleteCustomModel("custom_xxx", 1L);

        verify(modelConfigMapper).deleteById("custom_xxx");
    }

    @Test
    void deleteCustomModel_builtinModel_forbidden() {
        ModelConfig config = ModelConfig.builder()
            .id("gpt-4o")
            .userId(null)
            .isBuiltin(true)
            .build();

        when(modelConfigMapper.selectById("gpt-4o")).thenReturn(config);

        assertThatThrownBy(() -> modelConfigService.deleteCustomModel("gpt-4o", 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                assertThat(be.getMessage()).contains("不允许删除内置模型");
            });
    }

    @Test
    void deleteCustomModel_notFound_throws() {
        when(modelConfigMapper.selectById("custom_xxx")).thenReturn(null);

        assertThatThrownBy(() -> modelConfigService.deleteCustomModel("custom_xxx", 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MODEL_NOT_FOUND);
            });
    }

    @Test
    void deleteCustomModel_wrongUser_forbidden() {
        ModelConfig config = ModelConfig.builder()
            .id("custom_xxx")
            .userId(2L)   // 属于用户 2，但当前用户是 1
            .isBuiltin(false)
            .build();

        when(modelConfigMapper.selectById("custom_xxx")).thenReturn(config);

        assertThatThrownBy(() -> modelConfigService.deleteCustomModel("custom_xxx", 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            });
    }

    @Test
    void getFirstEnabledBuiltInModelId_returnsFirst() {
        ModelConfig gpt4o = ModelConfig.builder()
            .id("gpt-4o")
            .isBuiltin(true)
            .isEnabled(true)
            .build();

        when(modelConfigMapper.selectList(any()))
            .thenReturn(List.of(gpt4o));

        String result = modelConfigService.getFirstEnabledBuiltInModelId();

        assertThat(result).isEqualTo("gpt-4o");
    }

    @Test
    void getFirstEnabledBuiltInModelId_empty_returnsNull() {
        when(modelConfigMapper.selectList(any()))
            .thenReturn(List.of());

        String result = modelConfigService.getFirstEnabledBuiltInModelId();

        assertThat(result).isNull();
    }
}

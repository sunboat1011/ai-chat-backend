package com.example.aichat.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCustomModelRequest {

    @NotBlank
    @Size(max = 100)
    private String displayName;

    @NotBlank
    @Size(max = 200)
    @Pattern(regexp = "^https?://[^\\s/$.?#].[^\\s]*$", message = "API 地址格式错误")
    private String apiBaseUrl;

    private String apiKey;

    @NotBlank
    @Size(max = 64)
    private String modelName;

    @NotBlank
    @Pattern(regexp = "^(openai|anthropic|ollama|zhipuai|custom)$",
        message = "不支持的模型提供商")
    private String provider;
}

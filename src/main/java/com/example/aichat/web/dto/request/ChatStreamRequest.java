package com.example.aichat.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChatStreamRequest {
    @NotBlank(message = "会话ID不能为空")
    @Pattern(regexp = "^conv_[a-zA-Z0-9_-]+$", message = "会话ID格式错误")
    private String conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 100000, message = "消息内容过长")
    private String message;

    @NotBlank(message = "模型ID不能为空")
    private String modelId;

    private String systemPrompt;

    @DecimalMin(value = "0.0", message = "temperature 最小为 0")
    @DecimalMax(value = "2.0", message = "temperature 最大为 2")
    private BigDecimal temperature;

    @Min(value = 1, message = "maxTokens 最小为 1")
    @Max(value = 128000, message = "maxTokens 最大为 128000")
    private Integer maxTokens;

    @DecimalMin(value = "0.0", message = "topP 最小为 0")
    @DecimalMax(value = "1.0", message = "topP 最大为 1")
    private BigDecimal topP;
}

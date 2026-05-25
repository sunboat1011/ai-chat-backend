package com.example.aichat.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateConversationRequest {
    @Size(max = 200, message = "标题最长 200 字符")
    private String title;

    private String systemPrompt;

    private String modelId;

    @DecimalMin("0.0") @DecimalMax("2.0")
    private BigDecimal temperature;

    @Min(1) @Max(128000)
    private Integer maxTokens;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private BigDecimal topP;
}

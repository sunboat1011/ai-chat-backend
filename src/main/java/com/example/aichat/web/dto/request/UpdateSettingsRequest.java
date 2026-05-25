package com.example.aichat.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateSettingsRequest {
    @Pattern(regexp = "^(dark|light|system)$")
    private String theme;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    private String accentColor;

    @Size(max = 5000)
    private String defaultSystemPrompt;

    @DecimalMin("0.0") @DecimalMax("2.0")
    private BigDecimal defaultTemperature;

    @Min(1) @Max(128000)
    private Integer defaultMaxTokens;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private BigDecimal defaultTopP;

    private String defaultModelId;

    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$")
    private String language;
}

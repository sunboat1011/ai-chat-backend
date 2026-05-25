package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SettingsResponse {
    private String theme;
    private String accentColor;
    private String defaultSystemPrompt;
    private BigDecimal defaultTemperature;
    private Integer defaultMaxTokens;
    private BigDecimal defaultTopP;
    private String defaultModelId;
    private String language;
}

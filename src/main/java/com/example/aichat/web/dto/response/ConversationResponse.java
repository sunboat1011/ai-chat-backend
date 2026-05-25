package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ConversationResponse {
    private String id;
    private String title;
    private String systemPrompt;
    private String modelId;
    private BigDecimal temperature;
    private Integer maxTokens;
    private BigDecimal topP;
    private Instant createdAt;
    private Instant updatedAt;
}

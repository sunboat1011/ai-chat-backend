package com.example.aichat.web.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class MigrateRequest {
    private List<MigrateConversation> conversations;
    private MigrateSettings settings;
    private Map<String, String> drafts;

    @Data
    public static class MigrateConversation {
        private String id;
        private String title;
        private String systemPrompt;
        private String modelId;
        private BigDecimal temperature;
        private Integer maxTokens;
        private BigDecimal topP;
        private Long createdAt;
        private Long updatedAt;
        private List<MigrateMessage> messages;
    }

    @Data
    public static class MigrateMessage {
        private String id;
        private String role;
        private String content;
        private String modelId;
        private Long timestamp;
    }

    @Data
    public static class MigrateSettings {
        private String theme;
        private String accentColor;
        private String defaultSystemPrompt;
        private BigDecimal defaultTemperature;
        private Integer defaultMaxTokens;
        private BigDecimal defaultTopP;
        private String defaultModelId;
        private String language;
    }
}

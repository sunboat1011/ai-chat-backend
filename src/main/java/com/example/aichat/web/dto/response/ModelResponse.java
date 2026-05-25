package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelResponse {
    private String id;
    private String displayName;
    private String provider;
    private String apiBaseUrl;
    private String modelName;
    private Boolean isBuiltin;
    private Boolean isCustom;
    private Boolean isEnabled;
}

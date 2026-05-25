package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MessageResponse {
    private String id;
    private String role;
    private String content;
    private String modelId;
    private Instant createdAt;
}

package com.example.aichat.web.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateConversationRequest {
    @Size(max = 200)
    private String title;
    private String systemPrompt;
}

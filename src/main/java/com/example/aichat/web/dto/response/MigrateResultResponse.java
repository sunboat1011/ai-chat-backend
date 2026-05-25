package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MigrateResultResponse {
    private Integer importedConversations;
    private Integer importedMessages;
    private Integer importedModels;
}

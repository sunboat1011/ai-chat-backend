package com.example.aichat.web.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationDetailResponse {
    private ConversationResponse conversation;
    private PageResponse<MessageResponse> messages;
}

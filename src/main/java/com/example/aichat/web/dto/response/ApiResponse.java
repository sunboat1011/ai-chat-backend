package com.example.aichat.web.dto.response;

import com.example.aichat.common.util.RequestContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;
    private String requestId;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code("SUCCESS")
            .message("操作成功")
            .data(data)
            .requestId(RequestContext.getRequestId())
            .timestamp(Instant.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .requestId(RequestContext.getRequestId())
            .timestamp(Instant.now().toString())
            .build();
    }
}

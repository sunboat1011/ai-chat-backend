package com.example.aichat.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS("SUCCESS", "操作成功", 200),
    PARAM_ERROR("PARAM_ERROR", "请求参数错误", 400),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或Token已过期", 401),
    FORBIDDEN("FORBIDDEN", "无权访问", 403),
    NOT_FOUND("NOT_FOUND", "资源不存在", 404),
    CONFLICT("CONFLICT", "资源冲突", 409),
    MODEL_NOT_FOUND("MODEL_NOT_FOUND", "模型配置不存在", 404),
    MODEL_AUTH_FAILED("MODEL_AUTH_FAILED", "API Key无效", 401),
    MODEL_RATE_LIMITED("MODEL_RATE_LIMITED", "请求过于频繁", 429),
    MODEL_SERVICE_ERROR("MODEL_SERVICE_ERROR", "模型服务异常", 502),
    MODEL_TIMEOUT("MODEL_TIMEOUT", "模型响应超时", 504),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;
}

package com.example.aichat.common.exception;

import com.example.aichat.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("Business exception: {} - {}", code.getCode(), e.getMessage());
        return ResponseEntity
            .status(code.getHttpStatus())
            .body(ApiResponse.error(code.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        List<org.springframework.validation.FieldError> fieldErrors =
            e.getBindingResult().getFieldErrors();
        String message = fieldErrors.isEmpty()
            ? "请求参数错误"
            : fieldErrors.get(0).getField() + ": " + fieldErrors.get(0).getDefaultMessage();
        log.warn("Validation error: {}", message);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.PARAM_ERROR.getCode(), message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        List<org.springframework.validation.FieldError> fieldErrors = e.getFieldErrors();
        String message = fieldErrors.isEmpty()
            ? "请求参数错误"
            : fieldErrors.get(0).getField() + ": " + fieldErrors.get(0).getDefaultMessage();
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.PARAM_ERROR.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                "服务器内部错误，请稍后重试"
            ));
    }
}

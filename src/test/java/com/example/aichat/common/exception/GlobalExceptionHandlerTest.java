package com.example.aichat.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void handleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void handleValidationExceptionReturnsParamError() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_ERROR"))
            .andExpect(jsonPath("$.message").value("username: 不能为空"));
    }

    @Test
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务器内部错误，请稍后重试"));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/business")
        public void business() {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        }

        @PostMapping("/test/validation")
        public void validation(@Valid @RequestBody ValidationTestRequest req) {
            // 仅用于触发验证
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new RuntimeException("模拟意外异常");
        }
    }

    @Data
    static class ValidationTestRequest {
        @NotBlank(message = "不能为空")
        private String username;
    }
}

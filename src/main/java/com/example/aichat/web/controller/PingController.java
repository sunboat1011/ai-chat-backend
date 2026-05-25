package com.example.aichat.web.controller;

import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.web.dto.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(Map.of(
            "message", "pong",
            "userId", String.valueOf(user.getId()),
            "username", user.getUsername()
        ));
    }
}

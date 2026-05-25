package com.example.aichat.web.controller;

import com.example.aichat.auth.service.AuthService;
import com.example.aichat.web.dto.request.LoginRequest;
import com.example.aichat.web.dto.request.RegisterRequest;
import com.example.aichat.web.dto.response.ApiResponse;
import com.example.aichat.web.dto.response.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}

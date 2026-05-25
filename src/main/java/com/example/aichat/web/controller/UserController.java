package com.example.aichat.web.controller;

import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.user.service.MigrateService;
import com.example.aichat.user.service.SettingsService;
import com.example.aichat.user.service.UserService;
import com.example.aichat.web.dto.request.MigrateRequest;
import com.example.aichat.web.dto.request.UpdateSettingsRequest;
import com.example.aichat.web.dto.response.ApiResponse;
import com.example.aichat.web.dto.response.MigrateResultResponse;
import com.example.aichat.web.dto.response.SettingsResponse;
import com.example.aichat.web.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SettingsService settingsService;
    private final MigrateService migrateService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(userService.getCurrentUser(user.getId()));
    }

    @GetMapping("/me/settings")
    public ApiResponse<SettingsResponse> getSettings(
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(settingsService.getSettings(user.getId()));
    }

    @PutMapping("/me/settings")
    public ApiResponse<SettingsResponse> updateSettings(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(settingsService.updateSettings(user.getId(), request));
    }

    @PostMapping("/migrate")
    public ApiResponse<MigrateResultResponse> migrate(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody MigrateRequest request) {
        return ApiResponse.success(migrateService.migrate(user.getId(), request));
    }
}

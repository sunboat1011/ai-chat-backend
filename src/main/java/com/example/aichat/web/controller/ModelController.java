package com.example.aichat.web.controller;

import com.example.aichat.auth.security.UserPrincipal;
import com.example.aichat.model.service.ModelConfigService;
import com.example.aichat.web.dto.request.CreateCustomModelRequest;
import com.example.aichat.web.dto.request.UpdateCustomModelRequest;
import com.example.aichat.web.dto.response.ApiResponse;
import com.example.aichat.web.dto.response.ModelResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ApiResponse<List<ModelResponse>> list(
            @AuthenticationPrincipal UserPrincipal user) {
        List<ModelResponse> models = modelConfigService.getAvailableModels(user.getId());
        return ApiResponse.success(models);
    }

    @PostMapping("/custom")
    public ApiResponse<ModelResponse> createCustom(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody CreateCustomModelRequest request) {
        ModelResponse model = modelConfigService.createCustomModel(request, user.getId());
        return ApiResponse.success(model);
    }

    @PutMapping("/custom/{id}")
    public ApiResponse<ModelResponse> updateCustom(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id,
            @Valid @RequestBody UpdateCustomModelRequest request) {
        ModelResponse model = modelConfigService.updateCustomModel(id, request, user.getId());
        return ApiResponse.success(model);
    }

    @DeleteMapping("/custom/{id}")
    public ApiResponse<Void> deleteCustom(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String id) {
        modelConfigService.deleteCustomModel(id, user.getId());
        return ApiResponse.success(null);
    }
}

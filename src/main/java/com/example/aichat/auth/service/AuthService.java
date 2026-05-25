package com.example.aichat.auth.service;

import com.example.aichat.auth.entity.User;
import com.example.aichat.auth.mapper.UserMapper;
import com.example.aichat.auth.security.JwtTokenProvider;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.model.service.ModelConfigService;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.LoginRequest;
import com.example.aichat.web.dto.request.RegisterRequest;
import com.example.aichat.web.dto.response.TokenResponse;
import com.example.aichat.web.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ModelConfigService modelConfigService;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userMapper.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱已被注册");
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .build();
        userMapper.insert(user);

        // 初始化默认用户设置
        UserSettings settings = createDefaultSettings(user.getId());
        userSettingsMapper.insert(settings);

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());
        return buildTokenResponse(token, user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());
        return buildTokenResponse(token, user);
    }

    private UserSettings createDefaultSettings(Long userId) {
        String defaultModelId = modelConfigService.getFirstEnabledBuiltInModelId();
        return UserSettings.builder()
            .userId(userId)
            .theme("system")
            .accentColor("#3b82f6")
            .defaultModelId(defaultModelId)
            .defaultTemperature(new BigDecimal("0.7"))
            .defaultMaxTokens(2048)
            .defaultTopP(new BigDecimal("1.0"))
            .language("zh-CN")
            .build();
    }

    private TokenResponse buildTokenResponse(String token, User user) {
        return TokenResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(604800L)
            .user(UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .createdAt(user.getCreatedAt())
                .build())
            .build();
    }
}

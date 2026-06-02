package com.example.aichat.auth.service;

import com.example.aichat.auth.entity.Role;
import com.example.aichat.auth.entity.User;
import com.example.aichat.auth.entity.UserRole;
import com.example.aichat.auth.mapper.RoleMapper;
import com.example.aichat.auth.mapper.UserMapper;
import com.example.aichat.auth.mapper.UserRoleMapper;
import com.example.aichat.auth.security.JwtTokenProvider;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.user.entity.UserSettings;
import com.example.aichat.user.mapper.UserSettingsMapper;
import com.example.aichat.web.dto.request.LoginRequest;
import com.example.aichat.web.dto.request.RegisterRequest;
import com.example.aichat.web.dto.response.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.example.aichat.model.service.ModelConfigService modelConfigService;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
            "test-secret-key-must-be-at-least-32-characters-long-for-hs256");
        tokenProvider.init();
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("alice123");

        when(userMapper.existsByUsername("alice")).thenReturn(false);
        when(userMapper.existsByEmail("alice@example.com")).thenReturn(false);
        when(modelConfigService.getFirstEnabledBuiltInModelId()).thenReturn("gpt-4o-mini");
        when(passwordEncoder.encode("alice123")).thenReturn("$2a$10$encoded");
        when(roleMapper.findByCode("USER")).thenReturn(Optional.of(
            Role.builder().id(1L).roleCode("USER").roleName("普通用户").build()));
        // mock insert 回填 auto-increment ID（模拟 MyBatis-Plus 行为）
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(tokenProvider.generateToken(anyLong(), eq("alice"))).thenReturn("mock-jwt-token");

        TokenResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getUsername()).isEqualTo("alice");

        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper).insert(any(UserRole.class));
        verify(userSettingsMapper).insert(any(UserSettings.class));
    }

    @Test
    void register_usernameExists_throwsParamError() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("alice123");

        when(userMapper.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR);
                assertThat(be.getMessage()).contains("用户名已存在");
            });
    }

    @Test
    void register_emailExists_throwsParamError() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("alice123");

        when(userMapper.existsByUsername("alice")).thenReturn(false);
        when(userMapper.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR);
            });
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("alice123");

        User user = User.builder()
            .id(1L)
            .username("alice")
            .passwordHash("$2a$10$encoded")
            .build();
        user.setCreatedAt(Instant.now());

        when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("alice123", "$2a$10$encoded")).thenReturn(true);
        when(tokenProvider.generateToken(1L, "alice")).thenReturn("mock-jwt-token");

        TokenResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(1L);
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrongpass");

        User user = User.builder()
            .username("alice")
            .passwordHash("$2a$10$encoded")
            .build();

        when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$10$encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                assertThat(be.getMessage()).contains("用户名或密码错误");
            });
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("pass123");

        when(userMapper.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
            });
    }
}

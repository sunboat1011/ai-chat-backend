package com.example.aichat.user.service;

import com.example.aichat.auth.entity.User;
import com.example.aichat.auth.mapper.UserMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.web.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void getCurrentUser_success() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .avatar("https://example.com/avatar.png")
                .build();
        user.setCreatedAt(Instant.now());

        when(userMapper.selectById(1L)).thenReturn(user);

        UserResponse response = userService.getCurrentUser(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAvatar()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void getCurrentUser_notFound_throws() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getCurrentUser(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });
    }

    @Test
    void toResponse_mapsAllFields() {
        User user = User.builder()
                .id(2L)
                .username("bob")
                .email("bob@example.com")
                .avatar("avatar.png")
                .build();
        user.setCreatedAt(Instant.now());

        UserResponse response = userService.toResponse(user);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("bob");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");
        assertThat(response.getAvatar()).isEqualTo("avatar.png");
    }
}

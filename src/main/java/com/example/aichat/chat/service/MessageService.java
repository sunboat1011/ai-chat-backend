package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.Message;
import com.example.aichat.chat.mapper.MessageMapper;
import com.example.aichat.common.exception.BusinessException;
import com.example.aichat.common.exception.ErrorCode;
import com.example.aichat.web.dto.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    @Transactional
    public void deleteMessage(String id) {
        Message message = messageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        messageMapper.softDelete(id, Instant.now());
    }

    @Transactional
    public MessageResponse restoreMessage(String id) {
        Instant fiveSecondsAgo = Instant.now().minus(5, ChronoUnit.SECONDS);
        int rows = messageMapper.restore(id, fiveSecondsAgo);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "恢复失败，已超过 5 秒撤销窗口");
        }
        Message message = messageMapper.selectById(id);
        return toResponse(message);
    }

    public List<MessageResponse> toResponseList(List<Message> messages) {
        return messages.stream().map(this::toResponse).toList();
    }

    public MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .modelId(message.getModelId())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

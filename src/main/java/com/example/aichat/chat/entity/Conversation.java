package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.entity.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

    @TableId
    private String id;

    private Long userId;

    private String title;

    private String systemPrompt;

    private String modelId;

    private BigDecimal temperature;

    private Integer maxTokens;

    private BigDecimal topP;

    private Instant deletedAt;
}

package com.example.aichat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.entity.BaseEntity;
import lombok.*;

import java.math.BigDecimal;

@TableName("user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String theme;

    private String accentColor;

    private String defaultSystemPrompt;

    private BigDecimal defaultTemperature;

    private Integer defaultMaxTokens;

    private BigDecimal defaultTopP;

    private String language;

    @TableField("default_model_id")
    private String defaultModelId;
}

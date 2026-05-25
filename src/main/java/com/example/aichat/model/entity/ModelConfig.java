package com.example.aichat.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.entity.BaseEntity;
import lombok.*;

@TableName("model_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelConfig extends BaseEntity {

    @TableId
    private String id;

    private Long userId;

    private String displayName;

    private String provider;

    private String apiBaseUrl;

    private String apiKey;

    private String modelName;

    private Boolean isBuiltin;

    private Boolean isEnabled;
}

package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.entity.BaseEntity;
import lombok.*;

import java.time.Instant;

@TableName("messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message extends BaseEntity {

    @TableId
    private String id;

    private String conversationId;

    private String role;

    private String content;

    private String modelId;

    private String parentId;

    private Boolean isDeleted;

    private Instant deletedAt;

    /** streaming / done / interrupted / error. 默认值 done。 */
    private String status;
}

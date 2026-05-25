package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 分页查询用户的非删除会话（按 updated_at 降序）
     */
    default IPage<Conversation> selectByUserIdPage(Page<Conversation> page, Long userId) {
        return selectPage(page, new QueryWrapper<Conversation>()
            .eq("user_id", userId)
            .isNull("deleted_at")
            .orderByDesc("updated_at"));
    }

    /**
     * 软删除
     */
    @Update("UPDATE conversations SET deleted_at = #{now} WHERE id = #{id} AND user_id = #{userId}")
    int softDelete(@Param("id") String id, @Param("userId") Long userId, @Param("now") Instant now);

    /**
     * 根据 ID + userId 查询非删除会话
     */
    default Conversation selectByIdAndUserId(String id, Long userId) {
        return selectOne(new QueryWrapper<Conversation>()
            .eq("id", id)
            .eq("user_id", userId)
            .isNull("deleted_at"));
    }
}

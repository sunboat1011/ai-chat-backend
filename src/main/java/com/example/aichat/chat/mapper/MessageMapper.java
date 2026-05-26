package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 分页查询会话消息（按 created_at 升序）
     */
    default IPage<Message> selectByConversationIdPage(Page<Message> page, String conversationId) {
        return selectPage(page, new QueryWrapper<Message>()
            .eq("conversation_id", conversationId)
            .eq("is_deleted", false)
            .orderByAsc("created_at"));
    }

    /**
     * 查询会话全部消息（不分页）
     */
    default List<Message> selectByConversationId(String conversationId) {
        return selectList(new QueryWrapper<Message>()
            .eq("conversation_id", conversationId)
            .eq("is_deleted", false)
            .orderByAsc("created_at"));
    }

    /**
     * 查询会话最近 N 条消息（按 created_at 降序取 N 条，再反转为升序）
     */
    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} AND is_deleted = false ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> selectRecentByConversationIdDesc(@Param("conversationId") String conversationId, @Param("limit") int limit);

    /**
     * 软删除单条消息
     */
    @Update("UPDATE messages SET is_deleted = true, deleted_at = #{now} WHERE id = #{id}")
    int softDelete(@Param("id") String id, @Param("now") Instant now);

    /**
     * 恢复消息（5 秒撤销窗口）
     */
    @Update("UPDATE messages SET is_deleted = false, deleted_at = null WHERE id = #{id} AND deleted_at >= #{since}")
    int restore(@Param("id") String id, @Param("since") Instant since);

    /**
     * 更新消息状态和内容
     */
    @Update("UPDATE messages SET status = #{status}, content = #{content} WHERE id = #{id}")
    int updateStatusAndContent(@Param("id") String id,
                                @Param("status") String status,
                                @Param("content") String content);
}

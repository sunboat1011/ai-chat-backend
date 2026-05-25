package com.example.aichat.model.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.model.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 查询用户可见的全部可用模型（内置 + 自定义）
     */
    @Select("SELECT * FROM model_configs WHERE (is_builtin = true OR user_id = #{userId}) AND is_enabled = true ORDER BY is_builtin DESC, created_at ASC")
    List<ModelConfig> selectAllAvailableByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的自定义模型
     */
    default List<ModelConfig> selectCustomByUserId(Long userId) {
        return selectList(new QueryWrapper<ModelConfig>()
            .eq("user_id", userId)
            .eq("is_builtin", false));
    }

    /**
     * 根据 ID + userId 查询自定义模型
     */
    default Optional<ModelConfig> selectByIdAndUserId(String id, Long userId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<ModelConfig>()
            .eq("id", id)
            .eq("user_id", userId)));
    }
}

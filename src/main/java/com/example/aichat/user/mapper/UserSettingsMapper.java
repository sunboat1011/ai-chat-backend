package com.example.aichat.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.user.entity.UserSettings;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {

    default Optional<UserSettings> findByUserId(Long userId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<UserSettings>()
            .eq("user_id", userId)));
    }
}

package com.example.aichat.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default Optional<User> findByUsername(String username) {
        return Optional.ofNullable(selectOne(new QueryWrapper<User>()
            .eq("username", username)));
    }

    default boolean existsByUsername(String username) {
        return exists(new QueryWrapper<User>().eq("username", username));
    }

    default boolean existsByEmail(String email) {
        return exists(new QueryWrapper<User>().eq("email", email));
    }
}

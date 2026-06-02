package com.example.aichat.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.auth.entity.User;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    // ===== 现有方法保留 =====
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

    // ===== 新增方法 =====

    /**
     * 根据ID查询用户，带角色列表（通过 JOIN 查询）
     */
    @Select("""
        SELECT u.*, r.role_code
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        LEFT JOIN roles r ON ur.role_id = r.id
        WHERE u.id = #{userId}
        """)
    List<UserWithRole> selectByIdWithRoles(Long userId);

    /**
     * 分页条件查询用户列表
     */
    default Page<User> selectByConditions(String keyword, String status, Page<User> page) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("username", keyword).or().like("email", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        return selectPage(page, wrapper);
    }

    // 内部 DTO，用于 JOIN 查询结果
    @Data
    class UserWithRole {
        private Long id;
        private String username;
        private String email;
        private String status;
        private String roleCode;
    }
}

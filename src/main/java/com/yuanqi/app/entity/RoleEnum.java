package com.yuanqi.app.entity;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoleEnum {

    USER(1, "普通用户", "ROLE_USER"),
    ADMIN(2, "管理员", "ROLE_ADMIN");

    @EnumValue // 用于 MyBatis-Plus 存储到数据库
    private final int code;

    @JsonValue // 用于前端展示
    private final String desc;

    private final String roleKey; // 用于权限框架校验的标识
}
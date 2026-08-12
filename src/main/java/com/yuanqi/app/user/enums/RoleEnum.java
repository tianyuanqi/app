package com.yuanqi.app.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举（预留权限扩展）。
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    USER(1, "普通用户", "ROLE_USER"),
    ADMIN(2, "管理员", "ROLE_ADMIN");

    @EnumValue
    private final int code;

    @JsonValue
    private final String desc;

    /** Spring Security 风格角色键 */
    private final String roleKey;
}

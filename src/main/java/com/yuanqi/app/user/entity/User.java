package com.yuanqi.app.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 用户实体，对应表 t_user。
 * <p>认证相关字段：账号状态、失败次数、锁定截止、改密与登录时间。</p>
 */
@Data
@TableName("t_user")
public class User {

    /** 系统内部主键（对外优先使用 uid） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外展示的业务 UID */
    private String uid;

    private String username;
    private String password;
    private Date birth;
    private int gender;
    private String email;

    /** 个人简介 */
    private String bio;

    /** 头像 URL */
    private String avatarUrl;

    /** 角色：USER / ADMIN */
    private String role;

    /** 账号状态：ACTIVE / LOCKED / DISABLED */
    private String accountStatus;

    /** 连续登录失败次数 */
    private Integer failedLoginCount;

    /** 锁定截止时间，到期后允许再次尝试登录 */
    private LocalDateTime lockedUntil;

    /** 最近一次修改密码时间 */
    private LocalDateTime passwordChangedAt;

    /** 最近一次登录成功时间 */
    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

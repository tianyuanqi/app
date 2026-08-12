package com.yuanqi.app.user.enums;

/**
 * 账号状态枚举。
 * <p>ACTIVE 可登录；LOCKED 为失败锁定；DISABLED 为管理员禁用。</p>
 */
public enum AccountStatus {
    /** 正常可用 */
    ACTIVE,
    /** 临时锁定（失败次数过多） */
    LOCKED,
    /** 管理员禁用 */
    DISABLED
}

package com.yuanqi.app.auth.support;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 注册/改密共用的账号策略校验。
 */
public final class AuthPolicy {

    /** 字母开头，后接字母数字下划线，总长 3–32 */
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,31}$");

    /** 至少 8 位，同时包含字母与数字 */
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

    private AuthPolicy() {
    }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_POLICY,
                    "用户名需以字母开头，仅含字母数字下划线，长度 3–32");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY,
                    "密码至少 8 位，且需同时包含字母和数字");
        }
    }
}

package com.yuanqi.app.auth.support;

import com.yuanqi.app.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthPolicyTest {
    @Test
    void 密码必须同时包含字母和数字且满足字素长度() {
        assertDoesNotThrow(() -> AuthPolicy.validatePassword("照片abc123"));
        assertThrows(BusinessException.class, () -> AuthPolicy.validatePassword("abcdefgh"));
        assertThrows(BusinessException.class, () -> AuthPolicy.validatePassword("12345678"));
    }

    @Test
    void 用户名允许重复语义且拒绝保留名和双向控制符() {
        assertEquals("摄影师📷", AuthPolicy.validateUsername(" 摄影师📷 "));
        assertThrows(BusinessException.class, () -> AuthPolicy.validateUsername("admin"));
        assertThrows(BusinessException.class, () -> AuthPolicy.validateUsername("正常\u202E伪装"));
    }

    @Test
    void 初始用户名总是同时含字母和数字() {
        for (int i = 0; i < 100; i++) {
            String value = AuthPolicy.initialUsername().substring(2);
            org.junit.jupiter.api.Assertions.assertTrue(value.chars().anyMatch(Character::isLetter));
            org.junit.jupiter.api.Assertions.assertTrue(value.chars().anyMatch(Character::isDigit));
        }
    }
}

package com.yuanqi.app.auth.support;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.text.UnicodeText;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/** 冻结 R4 的密码、用户名和原因文本规则。 */
public final class AuthPolicy {
    private static final Set<String> RESERVED = Set.of(
            "系统", "管理员", "官方", "版主", "system", "admin", "administrator", "official", "moderator");
    private static final char[] INITIAL_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthPolicy() {
    }

    public static String validateUsername(String input) {
        String display = UnicodeText.nfc(UnicodeText.trimUnicode(input));
        int count = UnicodeText.graphemeCount(display);
        if (count < 1 || count > 20 || UnicodeText.containsForbiddenControl(display, false)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户名必须为 1～20 个可见字符");
        }
        String reservedKey = UnicodeText.comparisonKey(UnicodeText.trimUnicode(display));
        if (RESERVED.contains(reservedKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该用户名不可使用");
        }
        return display;
    }

    public static void validatePassword(String password) {
        int count = UnicodeText.graphemeCount(password);
        boolean letter = password != null && password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password != null && password.codePoints().anyMatch(Character::isDigit);
        if (count < 8 || count > 64 || !letter || !digit || UnicodeText.containsForbiddenControl(password, false)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码必须为 8～64 个可见字符并同时包含字母和数字");
        }
    }

    public static String validateReason(String input) {
        String value = UnicodeText.nfc(UnicodeText.trimUnicode(input));
        int count = UnicodeText.graphemeCount(value);
        if (count < 1 || count > 500 || UnicodeText.containsForbiddenControl(value, false)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "原因必须为 1～500 个可见字符");
        }
        return value;
    }

    public static String initialUsername() {
        char[] suffix = new char[8];
        boolean hasLetter;
        boolean hasDigit;
        do {
            hasLetter = false;
            hasDigit = false;
            for (int i = 0; i < suffix.length; i++) {
                suffix[i] = INITIAL_ALPHABET[RANDOM.nextInt(INITIAL_ALPHABET.length)];
                hasLetter |= Character.isLetter(suffix[i]);
                hasDigit |= Character.isDigit(suffix[i]);
            }
        } while (!hasLetter || !hasDigit);
        return "用户" + new String(suffix);
    }
}

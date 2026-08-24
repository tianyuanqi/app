package com.yuanqi.app.auth.support;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.text.UnicodeText;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.util.Locale;

@Component
public class EmailNormalizer {
    public String normalize(String raw) {
        String email = UnicodeText.trimUnicode(raw);
        if (email == null || email.length() > 320) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "邮箱格式不正确");
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "邮箱格式不正确");
        }
        try {
            String local = email.substring(0, at).toLowerCase(Locale.ROOT);
            String domain = IDN.toASCII(email.substring(at + 1), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            return local + "@" + domain;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "邮箱格式不正确");
        }
    }
}

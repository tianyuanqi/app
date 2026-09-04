package com.yuanqi.app.common.http;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;

import java.util.regex.Pattern;

/** 强 ETag 的唯一解析与比较入口。 */
public final class StrongEtag {
    private static final Pattern VALUE = Pattern.compile("^\"[A-Za-z0-9._:-]+\"$");

    private StrongEtag() {
    }

    public static void requireMatch(String supplied, String current) {
        if (supplied == null || supplied.isBlank()) {
            throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED);
        }
        if (!VALUE.matcher(supplied).matches()) {
            throw new BusinessException(ErrorCode.INVALID_IF_MATCH);
        }
        if (!current.equals(supplied)) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED);
        }
    }
}

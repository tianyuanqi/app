package com.yuanqi.app.common.http;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class StrongEtagTest {
    @Test void 原始强ETag匹配成功() {
        assertThatCode(() -> StrongEtag.requireMatch("\"work-7\"", "\"work-7\""))
                .doesNotThrowAnyException();
    }

    @Test void 缺失非法和过期值使用不同错误码() {
        assertCode(null, ErrorCode.PRECONDITION_REQUIRED);
        assertCode("W/\"work-7\"", ErrorCode.INVALID_IF_MATCH);
        assertCode("*", ErrorCode.INVALID_IF_MATCH);
        assertCode("\"work-6\"", ErrorCode.PRECONDITION_FAILED);
    }

    private void assertCode(String supplied, ErrorCode expected) {
        BusinessException error = catchThrowableOfType(
                () -> StrongEtag.requireMatch(supplied, "\"work-7\""), BusinessException.class);
        assertThat(error.getErrorCode()).isEqualTo(expected);
    }
}

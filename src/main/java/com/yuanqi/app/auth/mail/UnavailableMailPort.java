package com.yuanqi.app.auth.mail;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class UnavailableMailPort implements MailPort {
    @Override
    public String sendRegistrationCode(String recipient, String code) {
        throw new BusinessException(ErrorCode.MAIL_DELIVERY_UNAVAILABLE,
                ErrorCode.MAIL_DELIVERY_UNAVAILABLE.getMessage(), true, null);
    }
}

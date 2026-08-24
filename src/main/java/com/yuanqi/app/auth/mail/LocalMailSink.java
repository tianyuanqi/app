package com.yuanqi.app.auth.mail;

import com.yuanqi.app.auth.support.CryptoSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 隔离环境 Mail Sink；不记录或输出邮箱、验证码。 */
@Component
@Profile({"local", "test"})
public class LocalMailSink implements MailPort {
    private final CryptoSupport cryptoSupport;
    private final Map<String, String> latestCodeByEmailKey = new ConcurrentHashMap<>();

    public LocalMailSink(CryptoSupport cryptoSupport) {
        this.cryptoSupport = cryptoSupport;
    }

    @Override
    public String sendRegistrationCode(String recipient, String code) {
        latestCodeByEmailKey.put(cryptoSupport.hmac(recipient), code);
        return UUID.randomUUID().toString();
    }

    /** 仅供隔离测试夹具读取；生产 Profile 不装配本类。 */
    public String readForTest(String normalizedEmail) {
        return latestCodeByEmailKey.get(cryptoSupport.hmac(normalizedEmail));
    }
}

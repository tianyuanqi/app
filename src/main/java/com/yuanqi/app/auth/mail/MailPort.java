package com.yuanqi.app.auth.mail;

public interface MailPort {
    String sendRegistrationCode(String recipient, String code);
}

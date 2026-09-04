package com.yuanqi.app.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 所有时间敏感业务统一依赖可替换 Clock。 */
@Configuration
public class ClockConfig {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}

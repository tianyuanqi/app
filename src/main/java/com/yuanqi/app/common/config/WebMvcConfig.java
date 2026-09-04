package com.yuanqi.app.common.config;

import com.yuanqi.app.auth.config.AuthProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 精确 Origin CORS；媒体只能经授权 Controller 读取，不暴露静态目录。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthProperties authProperties;

    public WebMvcConfig(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(authProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-CSRF-Token", "Idempotency-Key",
                        "If-Match", "Accept")
                .exposedHeaders("ETag", "Retry-After", "Location", "X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

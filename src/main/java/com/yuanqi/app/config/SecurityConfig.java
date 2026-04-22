package com.yuanqi.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 引入了spring-boot-starter-security依赖以后，会拦截所有未携带合法凭证的请求，
 * 这样导致使用postman调试接口也会被拦截，所以为了方便调试，编写此配置类，对指定几个接口进行放行
 */


@Configuration
@EnableWebSecurity // 开启 Web 安全功能
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF 跨站请求伪造保护（因为我们完全依赖 JWT，不需要这个老旧机制）
                .csrf(csrf -> csrf.disable())

                // 2. 配置请求拦截规则
                .authorizeHttpRequests(auth -> auth

                        // 全局放行所有的 OPTIONS 预检请求，解决复杂请求的跨域拦截
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ⚠️ 核心配置：开启“白名单”
                        // 放行登录接口、获取列表接口，以及所有的物理图片访问路径
                        .requestMatchers("/api/users/login",
                                "/api/photos/list",
                                "/api/photos/my-list",
                                "/uploads/**",
                                "/api/photos/upload",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()

                        // 其他任何请求（比如 /api/photos/upload）都必须经过认证才能访问
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
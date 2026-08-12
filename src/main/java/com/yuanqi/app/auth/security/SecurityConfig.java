package com.yuanqi.app.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：无状态 JWT，按方案 A 划分公开与需登录接口。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 认证公开接口
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/token/refresh"
                        ).permitAll()
                        // 用户资料：me 需登录，公开主页可读
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").permitAll()
                        // 发现首页公开
                        .requestMatchers("/api/v1/home/**").permitAll()
                        // 作品：先声明需登录的路径，再放行公开查询
                        .requestMatchers(HttpMethod.GET, "/api/v1/photos/mine", "/api/v1/photos/my-list").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/photos/*/submit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/photos", "/api/v1/photos/list", "/api/v1/photos/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/photos", "/api/v1/photos/upload").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/photos/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/photos/**").authenticated()
                        // 审核：仅登录后由服务层校验 ADMIN
                        .requestMatchers("/api/v1/moderation/**").authenticated()
                        // 分类、静态资源、文档与健康检查
                        .requestMatchers("/api/v1/categories", "/api/v1/categories/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.UNAUTHORIZED));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.FORBIDDEN));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

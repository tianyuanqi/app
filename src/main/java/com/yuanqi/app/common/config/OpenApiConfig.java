package com.yuanqi.app.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 全局定义：文档信息与 Bearer JWT 方案。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "2400px API",
                version = "1.0",
                description = "2400px v1.0 模块化单体 Backend；运行时 /v3/api-docs 为机器可读权威契约"
        )
)
@SecurityScheme(
        name = "Authorization",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {
}

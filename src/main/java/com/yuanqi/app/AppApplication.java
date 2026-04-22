package com.yuanqi.app;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


// 1. 定义全局文档信息，并要求所有接口默认需要名为 "Authorization" 的认证
@OpenAPIDefinition(
		info = @Info(title = "个人主页系统 API", version = "1.0", description = "天元齐的个人主页后端接口"),
		security = @SecurityRequirement(name = "Authorization") // 全局生效
)
// 2. 告诉 Swagger 这个 "Authorization" 是一个基于 HTTP Bearer 的 JWT Token
@SecurityScheme(
		name = "Authorization",
		type = SecuritySchemeType.HTTP,
		bearerFormat = "JWT",
		scheme = "bearer"
)
@SpringBootApplication
public class AppApplication {

	public static void main(String[] args) {

		SpringApplication.run(AppApplication.class, args);
	}




}

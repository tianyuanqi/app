package com.yuanqi.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // 告诉 Spring 这是一个配置类
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有接口被跨域访问
                .allowedOriginPatterns("*") // 允许所有来源（开发环境直接写*，生产环境再改）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的请求方式
                .allowedHeaders("*")
                .allowCredentials(true) // 是否允许携带 Cookie
                .maxAge(3600); // 预检请求的缓存时间
    }

    // ================= 本次新增：静态资源映射 =================
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 当请求类似 http://localhost:8080/uploads/xxx.jpg 时
        registry.addResourceHandler("/uploads/**")
                // 映射到您电脑本地的绝对路径（注意 file: 前缀和结尾的斜杠必不可少）
                .addResourceLocations("file:/Users/yuanqi/devTools/img/");
    }
}
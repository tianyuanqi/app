package com.yuanqi.app.vo;

import lombok.Data;

@Data
public class LoginVO {
    // 核心：用于后续接口鉴权的通行证
    private String token;

    // 基础信息：供前端快速渲染页面使用
    private Long userId;
    private String username;
    private String role; // 比如 "ADMIN" 或 "USER"，前端可以据此控制菜单按钮的显示与隐藏
}
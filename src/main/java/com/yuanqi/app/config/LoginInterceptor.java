package com.yuanqi.app.config;

import com.yuanqi.app.common.JwtUtils;
import com.yuanqi.app.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 统一提取 Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 2. 统一验证与解析
        Long userId = JwtUtils.getUserIdFromToken(token);

        if (userId == null) {
            // 如果校验失败，直接在这里拦截，不让请求往后走
            response.setStatus(401);
            response.getWriter().write("{\"code\":401, \"message\":\"Unauthorized: Please login first.\"}");
            return false;
        }

        // 3. 核心：将解析出的 userId 塞进当前线程的“口袋”
        UserContext.setUserId(userId);

        // 验证通过，放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求处理完后，一定要清理 ThreadLocal，这是职业素养
        UserContext.remove();
    }
}
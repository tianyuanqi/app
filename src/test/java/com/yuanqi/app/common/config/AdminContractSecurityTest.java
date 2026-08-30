package com.yuanqi.app.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminContractSecurityTest {
    @Autowired MockMvc mockMvc;

    @Test void 未认证读取在资源和ETag处理前返回认证错误() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/u_unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post("/api/v1/admin/users/u_unknown/disable")
                        .contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 普通用户不能读取管理员ETag资源() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/u_unknown"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/moderation/photos/w_unknown"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void HTTPBasic不启用且仍由自定义认证入口处理() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/u_unknown").header("Authorization", "Basic eDp4"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }
}

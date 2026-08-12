package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.AuthAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证审计日志 Mapper。
 */
@Mapper
public interface AuthAuditLogMapper extends BaseMapper<AuthAuditLog> {
}

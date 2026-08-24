package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 认证会话表 Mapper。
 */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
    @Select("SELECT * FROM auth_session WHERE session_id=#{sessionId} LIMIT 1")
    AuthSession findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM auth_session WHERE session_id=#{sessionId} LIMIT 1 FOR UPDATE")
    AuthSession findBySessionIdForUpdate(@Param("sessionId") String sessionId);
}

package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证会话表 Mapper。
 */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}

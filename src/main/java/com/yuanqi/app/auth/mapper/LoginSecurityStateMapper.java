package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.LoginSecurityState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoginSecurityStateMapper extends BaseMapper<LoginSecurityState> {
    @Select("SELECT * FROM login_security_state WHERE account_id=#{accountId} FOR UPDATE")
    LoginSecurityState findForUpdate(@Param("accountId") Long accountId);
}

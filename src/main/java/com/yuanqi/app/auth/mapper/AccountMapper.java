package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {
    @Select("SELECT * FROM user_account WHERE email_key=#{emailKey} LIMIT 1")
    Account findByEmailKey(@Param("emailKey") String emailKey);

    @Select("SELECT * FROM user_account WHERE email_key=#{emailKey} LIMIT 1 FOR UPDATE")
    Account findByEmailKeyForUpdate(@Param("emailKey") String emailKey);

    @Select("SELECT * FROM user_account WHERE uid=#{uid} LIMIT 1")
    Account findByUid(@Param("uid") String uid);

    @Select("SELECT * FROM user_account WHERE uid=#{uid} LIMIT 1 FOR UPDATE")
    Account findByUidForUpdate(@Param("uid") String uid);
}

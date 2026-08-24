package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.RefreshCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefreshCredentialMapper extends BaseMapper<RefreshCredential> {
    @Select("SELECT * FROM auth_refresh_token WHERE token_hash=#{hash} LIMIT 1 FOR UPDATE")
    RefreshCredential findByHashForUpdate(@Param("hash") String hash);

    @Select("SELECT * FROM auth_refresh_token WHERE token_hash=#{hash} LIMIT 1")
    RefreshCredential findByHash(@Param("hash") String hash);
}

package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.VerificationFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VerificationFlowMapper extends BaseMapper<VerificationFlow> {
    @Select("SELECT * FROM email_verification_flow WHERE flow_id=#{flowId} LIMIT 1 FOR UPDATE")
    VerificationFlow findByFlowIdForUpdate(@Param("flowId") String flowId);

    @Select("SELECT * FROM email_verification_flow WHERE email_key=#{emailKey} AND status='ACTIVE' " +
            "ORDER BY id DESC LIMIT 1 FOR UPDATE")
    VerificationFlow findActiveByEmailForUpdate(@Param("emailKey") String emailKey);
}

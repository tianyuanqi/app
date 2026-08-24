package com.yuanqi.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.entity.VerificationGeneration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VerificationGenerationMapper extends BaseMapper<VerificationGeneration> {
    @Select("SELECT * FROM email_verification_generation WHERE flow_id=#{flowId} AND generation=#{generation} LIMIT 1 FOR UPDATE")
    VerificationGeneration findForUpdate(@Param("flowId") Long flowId, @Param("generation") Integer generation);
}

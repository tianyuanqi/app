package com.yuanqi.app.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.user.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    @Select("SELECT * FROM user_profile WHERE account_id=#{accountId} FOR UPDATE")
    UserProfile findForUpdate(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM photo_work WHERE author_account_id=#{accountId} AND publication_state='PUBLISHED'")
    long countPublicWorks(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM photo_like l JOIN photo_work w ON w.id=l.work_id " +
            "WHERE w.author_account_id=#{accountId} AND w.publication_state='PUBLISHED'")
    long countReceivedLikes(@Param("accountId") Long accountId);
}

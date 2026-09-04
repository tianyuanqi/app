package com.yuanqi.app.interaction.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PhotoLikeMapper {
    @Insert("INSERT IGNORE INTO photo_like(work_id,account_id,created_at) VALUES(#{workId},#{accountId},#{at})")
    int like(@Param("workId") Long workId, @Param("accountId") Long accountId, @Param("at") LocalDateTime at);
    @Delete("DELETE FROM photo_like WHERE work_id=#{workId} AND account_id=#{accountId}")
    int unlike(@Param("workId") Long workId, @Param("accountId") Long accountId);
    @Select("SELECT COUNT(*) FROM photo_like WHERE work_id=#{workId}")
    long count(@Param("workId") Long workId);
    @Select("SELECT COUNT(*)>0 FROM photo_like WHERE work_id=#{workId} AND account_id=#{accountId}")
    boolean exists(@Param("workId") Long workId, @Param("accountId") Long accountId);
    @Select("SELECT COUNT(*) FROM photo_like l JOIN photo_work w ON w.id=l.work_id WHERE w.author_account_id=#{accountId}")
    long received(@Param("accountId") Long accountId);
}

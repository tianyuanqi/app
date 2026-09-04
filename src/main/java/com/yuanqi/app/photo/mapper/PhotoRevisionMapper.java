package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.PhotoRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PhotoRevisionMapper extends BaseMapper<PhotoRevision> {
    @Select("SELECT * FROM photo_revision WHERE revision_id=#{revisionId} LIMIT 1")
    PhotoRevision findByPublicId(@Param("revisionId") String revisionId);
    @Select("SELECT COALESCE(MAX(revision_number),0) FROM photo_revision WHERE work_id=#{workId}")
    int maxRevisionNumber(@Param("workId") Long workId);
}

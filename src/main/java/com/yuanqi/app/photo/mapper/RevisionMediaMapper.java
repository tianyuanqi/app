package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.RevisionMedia;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RevisionMediaMapper extends BaseMapper<RevisionMedia> {
    @Select("SELECT * FROM revision_media WHERE revision_id=#{revisionId} ORDER BY position")
    List<RevisionMedia> listByRevision(@Param("revisionId") Long revisionId);
    @Delete("DELETE FROM revision_media WHERE revision_id=#{revisionId}")
    int deleteByRevision(@Param("revisionId") Long revisionId);
}

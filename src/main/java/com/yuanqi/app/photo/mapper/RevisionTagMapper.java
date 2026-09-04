package com.yuanqi.app.photo.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RevisionTagMapper {
    @Delete("DELETE FROM revision_tag WHERE revision_id=#{revisionId}") int deleteByRevision(@Param("revisionId") Long revisionId);
    @Insert("INSERT INTO revision_tag(revision_id,tag_id,position) VALUES(#{revisionId},#{tagId},#{position})")
    int insert(@Param("revisionId") Long revisionId,@Param("tagId") Long tagId,@Param("position") int position);
}

package com.yuanqi.app.photo.mapper;

import com.yuanqi.app.photo.entity.PhotoTagRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhotoTagRelationMapper {
    @Insert("INSERT INTO t_photo_tag(photo_id,tag_id) VALUES(#{photoId},#{tagId})")
    int insertRelation(@Param("photoId") Long photoId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM t_photo_tag WHERE photo_id=#{photoId}")
    int deleteByPhotoId(@Param("photoId") Long photoId);

    @Select("SELECT photo_id AS photoId,tag_id AS tagId FROM t_photo_tag WHERE photo_id=#{photoId}")
    List<PhotoTagRelation> listByPhotoId(@Param("photoId") Long photoId);

    @Select("<script>SELECT photo_id AS photoId,tag_id AS tagId FROM t_photo_tag WHERE photo_id IN "
            + "<foreach collection='photoIds' item='photoId' open='(' separator=',' close=')'>#{photoId}</foreach></script>")
    List<PhotoTagRelation> listByPhotoIds(@Param("photoIds") List<Long> photoIds);

    @Select("<script>SELECT photo_id AS photoId,tag_id AS tagId FROM t_photo_tag WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>#{tagId}</foreach></script>")
    List<PhotoTagRelation> listByTagIds(@Param("tagIds") List<Long> tagIds);
}

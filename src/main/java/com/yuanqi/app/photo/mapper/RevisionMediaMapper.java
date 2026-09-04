package com.yuanqi.app.photo.mapper;

import com.yuanqi.app.photo.entity.RevisionMedia;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RevisionMediaMapper {
    @Insert("INSERT INTO revision_media(revision_id,media_id,position,capture_time,camera_body,lens,focal_length,aperture,shutter_speed,iso_value,parameter_source,warning_codes) "
            + "VALUES(#{revisionId},#{mediaId},#{position},#{captureTime},#{cameraBody},#{lens},#{focalLength},#{aperture},#{shutterSpeed},#{isoValue},#{parameterSource},#{warningCodes})")
    int insertRelation(RevisionMedia relation);

    @Select("SELECT revision_id AS revisionId,media_id AS mediaId,position,capture_time AS captureTime,camera_body AS cameraBody,lens,focal_length AS focalLength,aperture,shutter_speed AS shutterSpeed,iso_value AS isoValue,parameter_source AS parameterSource,warning_codes AS warningCodes FROM revision_media WHERE revision_id=#{revisionId} ORDER BY position")
    List<RevisionMedia> listByRevision(@Param("revisionId") Long revisionId);
    @Delete("DELETE FROM revision_media WHERE revision_id=#{revisionId}")
    int deleteByRevision(@Param("revisionId") Long revisionId);
}

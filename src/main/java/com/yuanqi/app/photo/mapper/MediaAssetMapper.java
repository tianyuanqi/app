package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.MediaAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
    @Select("SELECT * FROM media_asset WHERE media_id=#{mediaId} LIMIT 1")
    MediaAsset findByPublicId(@Param("mediaId") String mediaId);

    @Select("SELECT * FROM media_asset WHERE media_id=#{mediaId} LIMIT 1 FOR UPDATE")
    MediaAsset findByPublicIdForUpdate(@Param("mediaId") String mediaId);

    @Select("SELECT (EXISTS(SELECT 1 FROM revision_media rm JOIN photo_revision r ON r.id=rm.revision_id " +
            "JOIN photo_work w ON w.public_revision_id=r.id WHERE rm.media_id=#{mediaId} AND w.publication_state='PUBLISHED') " +
            "OR EXISTS(SELECT 1 FROM user_profile p WHERE p.avatar_media_id=#{mediaId}))")
    boolean isPublicWeb(@Param("mediaId") Long mediaId);

    @Select("SELECT EXISTS(SELECT 1 FROM revision_media rm JOIN photo_revision r ON r.id=rm.revision_id " +
            "JOIN photo_work w ON w.working_revision_id=r.id WHERE rm.media_id=#{mediaId} " +
            "AND r.state='PENDING')")
    boolean isPendingReviewWeb(@Param("mediaId") Long mediaId);

    @Select("SELECT COUNT(*)>0 FROM revision_media WHERE media_id=#{mediaId}")
    boolean isReferenced(@Param("mediaId") Long mediaId);
}

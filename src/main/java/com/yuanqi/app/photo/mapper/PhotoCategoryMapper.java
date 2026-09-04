package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.PhotoCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PhotoCategoryMapper extends BaseMapper<PhotoCategory> {

    @org.apache.ibatis.annotations.Select("SELECT c.public_id AS categoryId, c.name AS name, " +
            "(c.active=1) AS selectable, " +
            "(c.active=1 OR EXISTS(SELECT 1 FROM photo_revision r JOIN photo_work w ON w.public_revision_id=r.id " +
            "WHERE r.category_id=c.id AND w.publication_state='PUBLISHED')) AS filterable " +
            "FROM photo_category c ORDER BY c.sort_order,c.public_id")
    java.util.List<java.util.Map<String,Object>> listViews();

    @org.apache.ibatis.annotations.Select("SELECT * FROM photo_category WHERE public_id=#{publicId} LIMIT 1")
    PhotoCategory findByPublicId(@org.apache.ibatis.annotations.Param("publicId") String publicId);
}

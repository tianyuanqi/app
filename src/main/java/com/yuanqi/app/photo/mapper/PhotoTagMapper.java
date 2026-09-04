package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.PhotoTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PhotoTagMapper extends BaseMapper<PhotoTag> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM photo_tag WHERE normalized_name=#{name} LIMIT 1")
    PhotoTag findByNormalizedName(@org.apache.ibatis.annotations.Param("name") String name);
}

package com.yuanqi.app.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.photo.entity.PhotoWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PhotoWorkMapper extends BaseMapper<PhotoWork> {
    @Select("SELECT * FROM photo_work WHERE work_id=#{workId} LIMIT 1")
    PhotoWork findByPublicId(@Param("workId") String workId);
    @Select("SELECT * FROM photo_work WHERE work_id=#{workId} LIMIT 1 FOR UPDATE")
    PhotoWork findByPublicIdForUpdate(@Param("workId") String workId);
}

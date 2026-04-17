package com.yuanqi.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.entity.PhotoInfo; // 引入刚才写的实体类
import org.apache.ibatis.annotations.Mapper;

@Mapper // 告诉 Spring 这是一个用于操作数据库的接口
public interface PhotoInfoMapper extends BaseMapper<PhotoInfo> {
    // 继承 BaseMapper 后，您就已经免费获得了针对 photo_info 表的增删改查方法！
}
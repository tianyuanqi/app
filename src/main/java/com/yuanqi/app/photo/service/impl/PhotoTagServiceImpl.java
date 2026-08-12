package com.yuanqi.app.photo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import com.yuanqi.app.photo.service.PhotoTagService;
import org.springframework.stereotype.Service;

/**
 * 标签服务实现：不存在则创建。
 */
@Service
public class PhotoTagServiceImpl implements PhotoTagService {

    private final PhotoTagMapper photoTagMapper;

    public PhotoTagServiceImpl(PhotoTagMapper photoTagMapper) {
        this.photoTagMapper = photoTagMapper;
    }

    @Override
    public PhotoTag getOrCreate(String name) {
        PhotoTag tag = photoTagMapper.selectOne(new LambdaQueryWrapper<PhotoTag>().eq(PhotoTag::getName, name));
        if (tag != null) {
            return tag;
        }
        PhotoTag created = new PhotoTag();
        created.setName(name);
        photoTagMapper.insert(created);
        return created;
    }
}

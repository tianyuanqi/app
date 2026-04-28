package com.yuanqi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuanqi.app.entity.PhotoTag;
import com.yuanqi.app.mapper.PhotoTagMapper;
import com.yuanqi.app.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.service.PhotoTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class PhotoTagServiceImpl extends ServiceImpl<PhotoTagMapper, PhotoTag> implements PhotoTagService {

    @Autowired
    PhotoTagMapper photoTagMapper;

    /**
     * 查询数据库中是否存在该tag，如果不存在则直接创建一个
     *
     * @param name
     * @return
     */
    @Override
    public PhotoTag getOrCreate(String name) {

        PhotoTag tag = photoTagMapper.selectOne(new LambdaQueryWrapper<PhotoTag>().eq(PhotoTag::getName, name));

        if (tag != null) {
            return tag;
        } else {

            PhotoTag photoTag = new PhotoTag();
            photoTag.setName(name);
            photoTagMapper.insert(photoTag);
            return photoTag;
        }

    }
}

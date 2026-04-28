package com.yuanqi.app.service.impl;

import com.yuanqi.app.entity.PhotoCategory;
import com.yuanqi.app.mapper.PhotoCategoryMapper;
import com.yuanqi.app.service.PhotoCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 用于照片的分类管理，在上传照片的时候需要用户手动勾选照片的分类
 * 同时管理员在后台可以对分类进行增删改查
 * 当前阶段把分类写死在数据库，只提供查询接口，用来把分类信息返回到前端
 */
@Service
public class PhotoCategoryServiceImpl implements PhotoCategoryService {


    @Autowired
    PhotoCategoryMapper mapper;

    @Override
    public List<PhotoCategory> getAllPhotoCategory() {
        return mapper.selectList(null);
    }
}

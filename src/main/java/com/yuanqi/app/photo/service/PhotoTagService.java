package com.yuanqi.app.photo.service;

import com.yuanqi.app.photo.entity.PhotoTag;

/**
 * 标签服务：按名称获取或创建。
 */
public interface PhotoTagService {

    PhotoTag getOrCreate(String name);
}

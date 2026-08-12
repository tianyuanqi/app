package com.yuanqi.app.photo.service;

import com.yuanqi.app.photo.vo.CategoryVO;

import java.util.List;

/**
 * 分类查询服务。
 */
public interface PhotoCategoryService {

    List<CategoryVO> listAll();
}

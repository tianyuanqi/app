package com.yuanqi.app.photo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.photo.entity.PhotoCategory;
import com.yuanqi.app.photo.mapper.PhotoCategoryMapper;
import com.yuanqi.app.photo.service.PhotoCategoryService;
import com.yuanqi.app.photo.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类服务：当前仅提供列表查询。
 */
@Service
public class PhotoCategoryServiceImpl implements PhotoCategoryService {

    private final PhotoCategoryMapper photoCategoryMapper;

    public PhotoCategoryServiceImpl(PhotoCategoryMapper photoCategoryMapper) {
        this.photoCategoryMapper = photoCategoryMapper;
    }

    @Override
    public List<CategoryVO> listAll() {
        return photoCategoryMapper.selectList(new LambdaQueryWrapper<PhotoCategory>()
                        .orderByAsc(PhotoCategory::getSortorder))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private CategoryVO toVO(PhotoCategory category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSortOrder(category.getSortorder());
        return vo;
    }
}

package com.yuanqi.app.controller;

import com.yuanqi.app.entity.PhotoCategory;
import com.yuanqi.app.service.PhotoCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "2. 照片分类", description = "负责照片类型的新增，查询等操作")
@RestController
@RequestMapping("/api/categories")
public class PhotoCategoryController {

    @Autowired
    private PhotoCategoryService categoryService;

    @Operation(summary = "获取照片类型", description = "查询数据库中所有的照片类型，返回给前端让用户进行选择")
    @GetMapping("/list")
    public List<PhotoCategory> getAllCategory(){

        return categoryService.getAllPhotoCategory();
    }
}

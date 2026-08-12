package com.yuanqi.app.photo.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.photo.service.PhotoCategoryService;
import com.yuanqi.app.photo.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口：统一返回 Result + CategoryVO。
 */
@Tag(name = "4. 照片分类", description = "分类查询")
@RestController
@RequestMapping("/api/v1/categories")
public class PhotoCategoryController {

    private final PhotoCategoryService categoryService;

    public PhotoCategoryController(PhotoCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "获取照片分类列表")
    @GetMapping({"", "/list"})
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.listAll());
    }
}

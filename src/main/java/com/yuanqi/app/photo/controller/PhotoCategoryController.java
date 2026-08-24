package com.yuanqi.app.photo.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.photo.mapper.PhotoCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name="分类") @RestController @RequestMapping("/api/v1/categories")
public class PhotoCategoryController {
    private final PhotoCategoryMapper mapper;
    public PhotoCategoryController(PhotoCategoryMapper mapper){this.mapper=mapper;}
    @Operation(summary="分类列表；停用但仍被公开作品引用的分类保持可筛选")
    @GetMapping public Result<List<CategoryView>> list(){return Result.success(mapper.listViews().stream().map(m->new CategoryView(
            String.valueOf(m.get("publicId")),String.valueOf(m.get("name")),bool(m.get("selectable")),bool(m.get("filterable")))).toList());}
    private boolean bool(Object v){return v instanceof Boolean b?b:v instanceof Number n&&n.intValue()!=0;}
    public record CategoryView(String categoryId,String name,boolean selectable,boolean filterable){}
}

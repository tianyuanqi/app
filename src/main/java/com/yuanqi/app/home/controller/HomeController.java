package com.yuanqi.app.home.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.home.dto.HomeFeedRequest;
import com.yuanqi.app.home.service.HomeService;
import com.yuanqi.app.home.vo.HotTagVO;
import com.yuanqi.app.photo.vo.CategoryVO;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.util.List;

/**
 * 发现流首页接口。
 */
@Tag(name = "5. 发现首页", description = "作品流、分类入口、热门标签")
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "发现流 Feed", description = "仅返回已发布作品卡片")
    @GetMapping("/feed")
    public Result<IPage<PhotoCardVO>> feed(@Valid @ParameterObject @ModelAttribute HomeFeedRequest request) {
        return Result.success(homeService.feed(request));
    }

    @Operation(summary = "首页分类入口")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.success(homeService.categories());
    }

    @Operation(summary = "热门标签")
    @GetMapping("/hot-tags")
    public Result<List<HotTagVO>> hotTags(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(homeService.hotTags(limit == null ? 20 : limit));
    }
}

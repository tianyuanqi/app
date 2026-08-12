package com.yuanqi.app.photo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.photo.dto.PhotoRequests;
import com.yuanqi.app.photo.service.PhotoService;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作品接口：公开列表仅已发布；上传默认待审。
 */
@Tag(name = "3. 作品管理", description = "作品上传、检索与维护")
@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @Operation(summary = "分页搜索已发布作品", description = "返回轻量 PhotoCardVO")
    @GetMapping({"", "/list"})
    public Result<IPage<PhotoCardVO>> search(@Valid @ModelAttribute PhotoRequests.Search request) {
        return Result.success(photoService.search(request));
    }

    @Operation(summary = "分页获取当前用户作品", description = "可按 status 筛选，默认全部状态")
    @GetMapping({"/mine", "/my-list"})
    public Result<IPage<PhotoCardVO>> getMyPhotoList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(photoService.getMyPhotos(UserContext.getUserId(), current, pageSize, status));
    }

    @Operation(summary = "获取作品详情")
    @GetMapping("/{id}")
    public Result<PhotoDetailVO> detail(@PathVariable Long id) {
        return Result.success(photoService.getDetail(id, UserContext.getUserId()));
    }

    @Operation(summary = "编辑作品")
    @PutMapping("/{id}")
    public Result<PhotoDetailVO> update(@PathVariable Long id,
                                        @Valid @RequestBody PhotoRequests.Update request) {
        return Result.success(photoService.update(id, UserContext.getUserId(), request));
    }

    @Operation(summary = "删除作品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        photoService.delete(id, UserContext.getUserId());
        return Result.success(null);
    }

    @Operation(summary = "提交审核", description = "草稿或驳回作品可提交为 PENDING")
    @PostMapping("/{id}/submit")
    public Result<PhotoDetailVO> submit(@PathVariable Long id) {
        return Result.success(photoService.submit(id, UserContext.getUserId()));
    }

    @Operation(summary = "上传单张照片", description = "上传后默认为 PENDING，需审核通过后进入首页")
    @PostMapping(value = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PhotoDetailVO> upload(@Valid @ModelAttribute PhotoRequests.Upload request) {
        return Result.success(photoService.upload(request, UserContext.getUserId()));
    }
}

package com.yuanqi.app.controller; // 替换为您的包名

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.common.Result;
import com.yuanqi.app.common.UserContext;
import com.yuanqi.app.entity.PhotoInfo;
import com.yuanqi.app.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "2. 照片管理", description = "负责照片的上传、查询等操作")
@RestController // 标识这是一个返回 JSON 数据的接口类
@RequestMapping("/api/photos") // 定义基础路由
public class PhotoController {

    @Autowired
    private PhotoService photoService;


    // 完整的请求路径将是： GET /api/photos/list
    @Operation(summary = "获取照片列表", description = "查询数据库中所有的照片信息流")
    @GetMapping("/list")
    public Result<IPage<PhotoInfo>> getPhotoList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        IPage<PhotoInfo> pageData = photoService.getPhotoList(current, pageSize);
        // selectList(null) 代表无条件查询整张表的所有数据
        return Result.success(pageData);
    }


    @Operation(summary = "获取用户照片列表", description = "查询数据库中指定用户的所有照片")
    @GetMapping("/my-list")
    public Result<List<PhotoInfo>> getMyPhotoList() {
        Long userId = UserContext.getUserId();
        return Result.success(photoService.getMyphotoList(userId));
    }


    /*
        Swagger 会默认把所有的post请求参数识别为application/json,但对于需要上传实体文件的请求来说，这样的描述并不准确
        所以需要明确的指定请求格式（Consumes）
        // 👇 核心修改1：增加 consumes = MediaType.MULTIPART_FORM_DATA_VALUE

    */
    @Operation(summary = "上传单张照片", description = "用于上传照片到服务器，支持最大50M的图片上传")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // 1. 修改返回值类型为 Result<String>
    public Result<String> uploadPhoto(
            // 👇 核心修改2：用 @Parameter 描述每一个表单字段，方便生成swagger文件
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "照片标题") @RequestParam("title") String poto_title,
            @Parameter(description = "照片描述") @RequestParam("description") String photo_description,
            @Parameter(description = "拍摄位置") @RequestParam("location") String location,
            @Parameter(description = "照片分类") @RequestParam("category") int category,
            @Parameter(description = "照片标签") @RequestParam("tag") List<String> photoTag
    ) throws Exception {

        Long userId = UserContext.getUserId();


        String path = photoService.uploadPhoto(file, poto_title, photo_description, location, category, photoTag, userId);

        // 2. 使用 Result.success() 将返回信息包裹起来
        return Result.success("文件上传成功，真实路径为:" + path);
    }
}
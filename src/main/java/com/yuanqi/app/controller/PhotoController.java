package com.yuanqi.app.controller; // 替换为您的包名

import com.yuanqi.app.common.Result;
import com.yuanqi.app.entity.PhotoInfo;
import com.yuanqi.app.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController // 标识这是一个返回 JSON 数据的接口类
@RequestMapping("/api/photos") // 定义基础路由
public class PhotoController {

    @Autowired
    private PhotoService photoService;


    // 完整的请求路径将是： GET /api/photos/list
    @GetMapping("/list")
    public Result<List<PhotoInfo>> getPhotoList() {
        // selectList(null) 代表无条件查询整张表的所有数据
        return Result.success(photoService.getPhotoList());
    }


    @PostMapping("/upload")
    // 1. 修改返回值类型为 Result<String>
    public Result<String> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("cameraBody") String cameraBody) throws Exception {

        String path = photoService.uploadPhoto(file, title, cameraBody);

        // 2. 使用 Result.success() 将返回信息包裹起来
        return Result.success("文件上传成功，真实路径为:" + path);
    }
}
package com.yuanqi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuanqi.app.entity.PhotoInfo;
import com.yuanqi.app.entity.User;
import com.yuanqi.app.mapper.PhotoInfoMapper;
import com.yuanqi.app.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@Service
public class PhotoServiceImpl implements PhotoService {

    @Autowired
    private PhotoInfoMapper photoInfoMapper; // 注入 Mapper 依赖

    @Override
    public String uploadPhoto(MultipartFile file, String title, String cameraBody) throws Exception {
        if (file.isEmpty()){
            throw new IllegalArgumentException("文件不能为空");
        }

        // 1. 定义存储路径
        String uploadDir = "/Users/yuanqi/devTools/img/";
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File dest = new File(uploadDir + fileName);

        // 2. 将图片保存到硬盘
        file.transferTo(dest);

        // 3. 将信息存入数据库
        PhotoInfo photo = new PhotoInfo();
        photo.setTitle(title);
        photo.setCameraBody(cameraBody);
        photo.setImageUrl("/uploads/" + fileName); // 存入相对路径，方便后续前端展示

        photoInfoMapper.insert(photo);

        return dest.getAbsolutePath();
    }

    @Override
    public List<PhotoInfo> getPhotoList() {
        return photoInfoMapper.selectList(null);
    }

}

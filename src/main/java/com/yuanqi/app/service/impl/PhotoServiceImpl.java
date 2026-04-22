package com.yuanqi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.yuanqi.app.entity.PhotoInfo;
import com.yuanqi.app.mapper.PhotoInfoMapper;
import com.yuanqi.app.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class PhotoServiceImpl implements PhotoService {

    @Autowired
    private PhotoInfoMapper photoInfoMapper; // 注入 Mapper 依赖

    @Override
    public String uploadPhoto(MultipartFile file, String title, Long userId) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 1. 初始化 PhotoInfo 并绑定基础信息
        PhotoInfo photo = new PhotoInfo();
        photo.setUserId(userId);
        photo.setTitle(title);

        // ================= 获取照片信息 =================
        try (InputStream inputStream = file.getInputStream()) {
            // 让 metadata-extractor 读取图片数据流
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            // 获取包含光圈、快门、ISO等核心参数的 Exif 目录
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            // 获取相机信息的主目录
            ExifIFD0Directory mainDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);


            if (directory != null) {
                //抽取相机型号(比如 "NIKON Z f")
                String cameraBody = mainDirectory.getString(mainDirectory.TAG_MODEL);
                photo.setCameraBody(cameraBody != null ? cameraBody : "null");

                //抽取镜头信息(比如 "NIKKOR Z 24-120mm f/4 S")
                String lens = directory.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
                photo.setLens(lens != null ? lens : "null");


                // 抽取光圈 (比如 "f/2.8")
                String aperture = directory.getDescription(ExifSubIFDDirectory.TAG_FNUMBER);
                photo.setAperture(aperture != null ? aperture : "null");

                // 抽取快门速度 (比如 "1/100 sec")
                String shutter = directory.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                photo.setShutterSpeed(shutter != null ? shutter : "null");

                // 抽取焦距 (比如 "35 mm")
                String focalLength = directory.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                photo.setFocalLength(focalLength != null ? focalLength : "null");

                // 抽取 ISO
                Integer iso = directory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                photo.setIso(iso); // 如果没查到，iso 就是 null


                // 抽取解析拍摄时间
                Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    // 将 java.util.Date 转换为实体类中的 LocalDateTime
                    photo.setShoot_date(date.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime());
                }

            } else {
                // 如果图片被压缩去除了 Exif，或者干脆就是张普通截图,展示未知
                photo.setCameraBody("null");
                photo.setAperture("null");
                photo.setShutterSpeed("null");
                photo.setFocalLength("null");
                photo.setCameraBody("null");
                photo.setShoot_date(null);
            }
        } catch (Exception e) {
            // 解析元数据失败不能影响核心的上传流程，只打个日志即可
            System.err.println("【解析 Exif 失败】：" + e.getMessage());
        }


        /*
            开始上传照片
         */

        // 1. 定义存储路径
        String uploadDir = "/Users/yuanqi/devTools/img/";
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File dest = new File(uploadDir + fileName);

        // 2. 将图片保存到硬盘
        file.transferTo(dest);

        // 3. 将信息存入数据库
        photo.setImageUrl("/uploads/" + fileName); // 存入相对路径，方便后续前端展示

        //将解析出来的userId和照片绑定
        photo.setUserId(userId);
        photoInfoMapper.insert(photo);

        return dest.getAbsolutePath();
    }





    @Override
    public List<PhotoInfo> getPhotoList() {
        return photoInfoMapper.selectList(null);
    }

    @Override
    public List<PhotoInfo> getMyphotoList(Long userId) {

        // 1. 创建一个“条件构造器”（相当于在帮你写 WHERE 语句）
        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper<>();

        // 2. 拼接条件：WHERE user_id = 传进来的 userId
        // PhotoInfo::getUserId 是 Java8 的方法引用，MyBatis-Plus 会自动把它翻译成数据库里的 user_id 列名
        wrapper.eq(PhotoInfo::getUserId, userId);

        // 3. 把组装好的条件扔给 selectList 方法
        return photoInfoMapper.selectList(wrapper);
    }

}

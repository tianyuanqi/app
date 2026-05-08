package com.yuanqi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.yuanqi.app.entity.PhotoInfo;
import com.yuanqi.app.entity.PhotoTag;
import com.yuanqi.app.entity.PhotoCategory;
import com.yuanqi.app.entity.PhotoTagRelation;
import com.yuanqi.app.mapper.PhotoCategoryMapper;
import com.yuanqi.app.mapper.PhotoInfoMapper;
import com.yuanqi.app.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.service.PhotoService;
import com.yuanqi.app.service.PhotoTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class PhotoServiceImpl<P extends BaseMapper<PhotoTagRelation>, P1> implements PhotoService {

    @Autowired
    private PhotoInfoMapper photoInfoMapper;

    @Autowired
    private PhotoCategoryMapper photoCategoryMapper;
    @Autowired
    private PhotoTagService photoTagService;

    @Autowired
    PhotoTagRelationMapper photoTagRelationMapper;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public String uploadPhoto(MultipartFile file, String photo_title, String photo_description,
                              String location, int category, List<String> photoTags, Long userId) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        /**
         * 1. 初始化 PhotoInfo 并绑定基础信息
         */
        PhotoInfo photo = new PhotoInfo();
        photo.setUserId(userId);
        photo.setTitle(photo_title);
        photo.setDescription(photo_description);
        photo.setLocation(location);


        /**
         2. 给照片绑定分类
            查询从前端传入的category_id是否存在，如果不存在则抛出异常
         */
        boolean isExist = photoCategoryMapper.exists(new LambdaQueryWrapper<PhotoCategory>().eq(PhotoCategory::getId, category));
        if (!isExist) {
            throw new IllegalArgumentException("非法的参数，该分类不存在");
        }
        photo.setCategory_id(category);


        /**
         * 获取照片的Exif信息并存入PhotoInfo中
         */
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
                photo.setLens("null");
                photo.setAperture("null");
                photo.setShutterSpeed("null");
                photo.setFocalLength("null");
                photo.setIso(null);
                photo.setShoot_date(null);
            }
        } catch (Exception e) {
            // 解析元数据失败不能影响核心的上传流程，只打个日志即可
            System.err.println("【解析 Exif 失败】：" + e.getMessage());
        }


        /**
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


        /**
         * 标签处理
         */

        //1. 获取照片自增 ID
        Long photoId = photo.getId();
        List<PhotoTagRelation> list = new ArrayList<>();

        // 2. 解析前端标签：若标签不存在则新建，并构建中间表关联对象（暂存内存）
        for (String name : photoTags) {
            PhotoTag photoTag = photoTagService.getOrCreate(name);
            list.add(new PhotoTagRelation(photoId, photoTag.getId()));

        }
        // 3. 将照片与标签的关联关系持久化到中间表
        if (!list.isEmpty()) {
            for (PhotoTagRelation relation : list) {
                photoTagRelationMapper.insert(relation);
            }
        }


        return dest.getAbsolutePath();
    }


    @Override
    public IPage<PhotoInfo> getPhotoList(Integer current, Integer pageSize) {

        Page<PhotoInfo> page=new Page<>(current,pageSize);

        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper();
        wrapper.orderByDesc(PhotoInfo::getCreateTime);

        return photoInfoMapper.selectPage(page,wrapper);
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

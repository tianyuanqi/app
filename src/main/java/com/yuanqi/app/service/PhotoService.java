package com.yuanqi.app.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.entity.PhotoInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public interface PhotoService {

    String uploadPhoto(MultipartFile file, String photo_title, String photo_description, String location, int category,List<String> photoTag, Long userId) throws Exception;

    IPage<PhotoInfo> getPhotoList(Integer current, Integer pageSize);

    List<PhotoInfo> getMyphotoList(Long userId);

}

package com.yuanqi.app.service;


import com.yuanqi.app.entity.PhotoInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public interface PhotoService {

    String uploadPhoto(MultipartFile file,String title,String cameraBody,Long userId) throws Exception;

    List<PhotoInfo> getPhotoList();

    List<PhotoInfo> getMyphotoList(Long userId);
}

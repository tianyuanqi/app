package com.yuanqi.app.service;


import com.yuanqi.app.entity.PhotoTag;
import org.springframework.stereotype.Service;

@Service
public interface PhotoTagService {

    PhotoTag getOrCreate(String name);
}

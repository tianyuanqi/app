package com.yuanqi.app.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.user.dto.UserRequests;
import com.yuanqi.app.user.vo.UserProfileVO;
import com.yuanqi.app.user.vo.UserVO;

/**
 * 用户资料领域服务。
 */
public interface UserService {

    UserVO getProfile(Long userId);

    UserVO updateProfile(Long userId, UserRequests.UpdateProfile request);

    /** 公开主页资料（含已发布作品数） */
    UserProfileVO getPublicProfile(String uid);

    /** 用户公开作品墙 */
    IPage<PhotoCardVO> listPublicPhotos(String uid, Integer current, Integer pageSize);

    void changePassword(Long userId, UserRequests.ChangePassword request);
}

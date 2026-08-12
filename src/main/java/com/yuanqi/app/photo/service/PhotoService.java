package com.yuanqi.app.photo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.photo.dto.PhotoRequests;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;

/**
 * 作品领域服务。
 */
public interface PhotoService {

    PhotoDetailVO upload(PhotoRequests.Upload request, Long userId);

    /** 公开搜索：仅 PUBLISHED，返回卡片 */
    IPage<PhotoCardVO> search(PhotoRequests.Search request);

    /** 发现流：仅 PUBLISHED */
    IPage<PhotoCardVO> feed(PhotoRequests.Search request);

    /** 我的作品：全状态，可按 status 筛选 */
    IPage<PhotoCardVO> getMyPhotos(Long userId, Integer current, Integer pageSize, String status);

    /** 用户公开作品墙：仅该用户 PUBLISHED */
    IPage<PhotoCardVO> listPublishedByUid(String uid, Integer current, Integer pageSize);

    PhotoDetailVO getDetail(Long photoId, Long viewerUserId);

    PhotoDetailVO update(Long photoId, Long userId, PhotoRequests.Update request);

    void delete(Long photoId, Long userId);

    /** 作者将 DRAFT/REJECTED 提交为 PENDING */
    PhotoDetailVO submit(Long photoId, Long userId);
}

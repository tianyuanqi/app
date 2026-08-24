package com.yuanqi.app.moderation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.moderation.dto.ModerationRequests;
import com.yuanqi.app.photo.entity.PhotoInfo;
import com.yuanqi.app.photo.enums.PhotoStatus;
import com.yuanqi.app.photo.mapper.PhotoInfoMapper;
import com.yuanqi.app.photo.support.PhotoAssembler;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 内容审核服务：通过 / 驳回 / 下架。
 */
@Service
public class ModerationService {

    private final PhotoInfoMapper photoInfoMapper;
    private final UserMapper userMapper;
    private final PhotoAssembler photoAssembler;

    public ModerationService(PhotoInfoMapper photoInfoMapper,
                             UserMapper userMapper,
                             PhotoAssembler photoAssembler) {
        this.photoInfoMapper = photoInfoMapper;
        this.userMapper = userMapper;
        this.photoAssembler = photoAssembler;
    }

    /** 待审列表（仅管理员） */
    public IPage<PhotoCardVO> listPending(Long adminUserId, Integer current, Integer pageSize) {
        requireAdmin(adminUserId);
        int safeSize = Math.min(pageSize == null ? 10 : pageSize, 100);
        Page<PhotoInfo> page = new Page<>(current == null ? 1 : current, safeSize);
        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper<PhotoInfo>()
                .eq(PhotoInfo::getStatus, PhotoStatus.PENDING.name())
                .orderByAsc(PhotoInfo::getCreateTime);
        Page<PhotoInfo> source = photoInfoMapper.selectPage(page, wrapper);
        Page<PhotoCardVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setPages(source.getPages());
        result.setRecords(source.getRecords().stream().map(photoAssembler::toCard).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PhotoDetailVO approve(Long photoId, Long adminUserId) {
        requireAdmin(adminUserId);
        PhotoInfo photo = requirePhoto(photoId);
        if (!PhotoStatus.PENDING.name().equals(photo.getStatus())
                && !PhotoStatus.OFFLINE.name().equals(photo.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅待审或已下架作品可通过审核发布");
        }
        photo.setStatus(PhotoStatus.PUBLISHED.name());
        photo.setRejectReason(null);
        photo.setReviewedAt(LocalDateTime.now());
        photo.setReviewedBy(adminUserId);
        photoInfoMapper.updateById(photo);
        return photoAssembler.toDetail(photo);
    }

    @Transactional(rollbackFor = Exception.class)
    public PhotoDetailVO reject(Long photoId, Long adminUserId, ModerationRequests.Reject request) {
        requireAdmin(adminUserId);
        PhotoInfo photo = requirePhoto(photoId);
        if (!PhotoStatus.PENDING.name().equals(photo.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅待审作品可驳回");
        }
        photo.setStatus(PhotoStatus.REJECTED.name());
        photo.setRejectReason(request == null || request.getReason() == null ? null : request.getReason().trim());
        photo.setReviewedAt(LocalDateTime.now());
        photo.setReviewedBy(adminUserId);
        photoInfoMapper.updateById(photo);
        return photoAssembler.toDetail(photo);
    }

    @Transactional(rollbackFor = Exception.class)
    public PhotoDetailVO offline(Long photoId, Long adminUserId) {
        requireAdmin(adminUserId);
        PhotoInfo photo = requirePhoto(photoId);
        if (!PhotoStatus.PUBLISHED.name().equals(photo.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅已发布作品可下架");
        }
        photo.setStatus(PhotoStatus.OFFLINE.name());
        photo.setReviewedAt(LocalDateTime.now());
        photo.setReviewedBy(adminUserId);
        photoInfoMapper.updateById(photo);
        return photoAssembler.toDetail(photo);
    }

    private PhotoInfo requirePhoto(Long photoId) {
        PhotoInfo photo = photoInfoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        }
        return photo;
    }

    private void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }
}

package com.yuanqi.app.photo.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.photo.entity.PhotoCategory;
import com.yuanqi.app.photo.entity.PhotoInfo;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.entity.PhotoTagRelation;
import com.yuanqi.app.photo.mapper.PhotoCategoryMapper;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import com.yuanqi.app.photo.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 作品实体到 VO 的组装器，供列表/详情/首页复用。
 */
@Component
public class PhotoAssembler {

    private final UserMapper userMapper;
    private final PhotoCategoryMapper photoCategoryMapper;
    private final PhotoTagMapper photoTagMapper;
    private final PhotoTagRelationMapper photoTagRelationMapper;

    public PhotoAssembler(UserMapper userMapper,
                          PhotoCategoryMapper photoCategoryMapper,
                          PhotoTagMapper photoTagMapper,
                          PhotoTagRelationMapper photoTagRelationMapper) {
        this.userMapper = userMapper;
        this.photoCategoryMapper = photoCategoryMapper;
        this.photoTagMapper = photoTagMapper;
        this.photoTagRelationMapper = photoTagRelationMapper;
    }

    /** 组装列表卡片 */
    public PhotoCardVO toCard(PhotoInfo photo) {
        PhotoCardVO vo = new PhotoCardVO();
        vo.setId(photo.getId());
        vo.setTitle(photo.getTitle());
        vo.setImageUrl(photo.getImageUrl());
        // 缩略图暂与原图相同，后续可接多规格资源
        vo.setThumbUrl(photo.getImageUrl());
        vo.setLocation(photo.getLocation());
        vo.setStatus(photo.getStatus());
        vo.setCreateTime(photo.getCreateTime());
        vo.setLikeCount(0);
        vo.setFavoriteCount(0);

        User user = userMapper.selectById(photo.getUserId());
        if (user != null) {
            PhotoCardVO.Author author = new PhotoCardVO.Author();
            author.setUid(user.getUid());
            author.setUsername(user.getUsername());
            author.setAvatarUrl(user.getAvatarUrl());
            vo.setAuthor(author);
        }
        PhotoCategory category = photoCategoryMapper.selectById(photo.getCategory_id());
        if (category != null) {
            PhotoCardVO.Category categoryVO = new PhotoCardVO.Category();
            categoryVO.setId(category.getId());
            categoryVO.setName(category.getName());
            vo.setCategory(categoryVO);
        }
        return vo;
    }

    /** 组装详情（含 EXIF 与标签） */
    public PhotoDetailVO toDetail(PhotoInfo photo) {
        PhotoDetailVO vo = new PhotoDetailVO();
        vo.setId(photo.getId());
        vo.setTitle(photo.getTitle());
        vo.setDescription(photo.getDescription());
        vo.setImageUrl(photo.getImageUrl());
        vo.setLocation(photo.getLocation());
        vo.setCreateTime(photo.getCreateTime());
        vo.setShootDate(photo.getShoot_date());
        vo.setCameraBody(photo.getCameraBody());
        vo.setLens(photo.getLens());
        vo.setFocalLength(photo.getFocalLength());
        vo.setAperture(photo.getAperture());
        vo.setShutterSpeed(photo.getShutterSpeed());
        vo.setIso(photo.getIso());
        vo.setStatus(photo.getStatus());
        vo.setRejectReason(photo.getRejectReason());

        User user = userMapper.selectById(photo.getUserId());
        if (user != null) {
            PhotoDetailVO.Author author = new PhotoDetailVO.Author();
            author.setUid(user.getUid());
            author.setUsername(user.getUsername());
            vo.setAuthor(author);
        }
        PhotoCategory category = photoCategoryMapper.selectById(photo.getCategory_id());
        if (category != null) {
            PhotoDetailVO.Category categoryVO = new PhotoDetailVO.Category();
            categoryVO.setId(category.getId());
            categoryVO.setName(category.getName());
            vo.setCategory(categoryVO);
        }
        List<Long> tagIds = photoTagRelationMapper.selectList(new LambdaQueryWrapper<PhotoTagRelation>()
                        .eq(PhotoTagRelation::getPhotoId, photo.getId()))
                .stream().map(PhotoTagRelation::getTagId).toList();
        List<PhotoTag> tags = tagIds.isEmpty() ? List.of() : photoTagMapper.selectBatchIds(tagIds);
        vo.setTags(tags.stream().map(tag -> {
            PhotoDetailVO.Tag tagVO = new PhotoDetailVO.Tag();
            tagVO.setId(tag.getId());
            tagVO.setName(tag.getName());
            return tagVO;
        }).toList());
        return vo;
    }
}

package com.yuanqi.app.home.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.home.dto.HomeFeedRequest;
import com.yuanqi.app.home.vo.HotTagVO;
import com.yuanqi.app.photo.dto.PhotoRequests;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.entity.PhotoTagRelation;
import com.yuanqi.app.photo.enums.PhotoStatus;
import com.yuanqi.app.photo.mapper.PhotoInfoMapper;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import com.yuanqi.app.photo.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.photo.service.PhotoCategoryService;
import com.yuanqi.app.photo.service.PhotoService;
import com.yuanqi.app.photo.vo.CategoryVO;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.entity.PhotoInfo;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发现流首页服务：Feed、分类入口、热门标签。
 */
@Service
public class HomeService {

    private final PhotoService photoService;
    private final PhotoCategoryService photoCategoryService;
    private final PhotoTagMapper photoTagMapper;
    private final PhotoTagRelationMapper photoTagRelationMapper;
    private final PhotoInfoMapper photoInfoMapper;

    public HomeService(PhotoService photoService,
                       PhotoCategoryService photoCategoryService,
                       PhotoTagMapper photoTagMapper,
                       PhotoTagRelationMapper photoTagRelationMapper,
                       PhotoInfoMapper photoInfoMapper) {
        this.photoService = photoService;
        this.photoCategoryService = photoCategoryService;
        this.photoTagMapper = photoTagMapper;
        this.photoTagRelationMapper = photoTagRelationMapper;
        this.photoInfoMapper = photoInfoMapper;
    }

    /** 首页作品流（仅已发布） */
    public IPage<PhotoCardVO> feed(HomeFeedRequest request) {
        PhotoRequests.Search search = new PhotoRequests.Search();
        search.setCurrent(request.getCurrent());
        search.setPageSize(request.getPageSize());
        search.setCategoryId(request.getCategoryId());
        search.setTag(request.getTag());
        search.setKeyword(request.getKeyword());
        search.setSort(request.getSort());
        return photoService.feed(search);
    }

    /** 首页分类条 */
    public List<CategoryVO> categories() {
        return photoCategoryService.listAll();
    }

    /**
     * 热门标签：统计已发布作品关联次数，取 Top N。
     */
    public List<HotTagVO> hotTags(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        // 已发布作品 ID
        List<Long> publishedIds = photoInfoMapper.selectList(new LambdaQueryWrapper<PhotoInfo>()
                        .eq(PhotoInfo::getStatus, PhotoStatus.PUBLISHED.name())
                        .select(PhotoInfo::getId))
                .stream().map(PhotoInfo::getId).toList();
        if (publishedIds.isEmpty()) {
            return List.of();
        }
        List<PhotoTagRelation> relations = photoTagRelationMapper.listByPhotoIds(publishedIds);
        Map<Long, Long> countByTag = relations.stream()
                .collect(Collectors.groupingBy(PhotoTagRelation::getTagId, Collectors.counting()));
        if (countByTag.isEmpty()) {
            return List.of();
        }
        Map<Long, PhotoTag> tagMap = photoTagMapper.selectBatchIds(countByTag.keySet()).stream()
                .collect(Collectors.toMap(PhotoTag::getId, t -> t));
        return countByTag.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(safeLimit)
                .map(entry -> {
                    HotTagVO vo = new HotTagVO();
                    PhotoTag tag = tagMap.get(entry.getKey());
                    vo.setId(entry.getKey());
                    vo.setName(tag == null ? null : tag.getName());
                    vo.setPhotoCount(entry.getValue());
                    return vo;
                })
                .toList();
    }
}

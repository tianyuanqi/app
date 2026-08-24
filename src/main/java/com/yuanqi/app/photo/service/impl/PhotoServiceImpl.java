package com.yuanqi.app.photo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.config.UploadProperties;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.photo.dto.PhotoRequests;
import com.yuanqi.app.photo.entity.PhotoCategory;
import com.yuanqi.app.photo.entity.PhotoInfo;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.entity.PhotoTagRelation;
import com.yuanqi.app.photo.enums.PhotoStatus;
import com.yuanqi.app.photo.mapper.PhotoCategoryMapper;
import com.yuanqi.app.photo.mapper.PhotoInfoMapper;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import com.yuanqi.app.photo.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.photo.service.PhotoService;
import com.yuanqi.app.photo.service.PhotoTagService;
import com.yuanqi.app.photo.support.PhotoAssembler;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作品服务实现：上传默认待审、公开仅已发布、详情按可见性控制。
 */
@Service
public class PhotoServiceImpl implements PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoInfoMapper photoInfoMapper;
    private final PhotoCategoryMapper photoCategoryMapper;
    private final PhotoTagService photoTagService;
    private final PhotoTagRelationMapper photoTagRelationMapper;
    private final PhotoTagMapper photoTagMapper;
    private final UserMapper userMapper;
    private final UploadProperties uploadProperties;
    private final PhotoAssembler photoAssembler;

    public PhotoServiceImpl(PhotoInfoMapper photoInfoMapper,
                            PhotoCategoryMapper photoCategoryMapper,
                            PhotoTagService photoTagService,
                            PhotoTagRelationMapper photoTagRelationMapper,
                            PhotoTagMapper photoTagMapper,
                            UserMapper userMapper,
                            UploadProperties uploadProperties,
                            PhotoAssembler photoAssembler) {
        this.photoInfoMapper = photoInfoMapper;
        this.photoCategoryMapper = photoCategoryMapper;
        this.photoTagService = photoTagService;
        this.photoTagRelationMapper = photoTagRelationMapper;
        this.photoTagMapper = photoTagMapper;
        this.userMapper = userMapper;
        this.uploadProperties = uploadProperties;
        this.photoAssembler = photoAssembler;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PhotoDetailVO upload(PhotoRequests.Upload request, Long userId) {
        requireUserId(userId);
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件不能为空");
        }

        PhotoInfo photo = new PhotoInfo();
        photo.setUserId(userId);
        photo.setTitle(request.getTitle().trim());
        photo.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        photo.setLocation(request.getLocation() == null ? null : request.getLocation().trim());
        // 上传默认进入待审核，不直接出现在公开首页
        photo.setStatus(PhotoStatus.PENDING.name());

        boolean categoryExists = photoCategoryMapper.exists(new LambdaQueryWrapper<PhotoCategory>()
                .eq(PhotoCategory::getId, request.getCategory()));
        if (!categoryExists) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非法的参数，该分类不存在");
        }
        photo.setCategory_id(request.getCategory());

        fillExif(photo, file);
        String fileName = persistFile(file);
        photo.setImageUrl("/uploads/" + fileName);
        photoInfoMapper.insert(photo);

        List<String> tags = request.getTag() == null ? List.of() : request.getTag();
        for (String name : tags) {
            if (!hasText(name)) {
                continue;
            }
            PhotoTag photoTag = photoTagService.getOrCreate(name.trim());
            photoTagRelationMapper.insert(new PhotoTagRelation(photo.getId(), photoTag.getId()));
        }
        return photoAssembler.toDetail(photo);
    }

    @Override
    public IPage<PhotoCardVO> search(PhotoRequests.Search request) {
        return queryPublishedCards(request);
    }

    @Override
    public IPage<PhotoCardVO> feed(PhotoRequests.Search request) {
        return queryPublishedCards(request);
    }

    @Override
    public IPage<PhotoCardVO> getMyPhotos(Long userId, Integer current, Integer pageSize, String status) {
        requireUserId(userId);
        int safeSize = Math.min(pageSize == null ? 10 : pageSize, 100);
        Page<PhotoInfo> page = new Page<>(current == null ? 1 : current, safeSize);
        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper<PhotoInfo>()
                .eq(PhotoInfo::getUserId, userId)
                .orderByDesc(PhotoInfo::getCreateTime);
        if (hasText(status)) {
            validateStatus(status);
            wrapper.eq(PhotoInfo::getStatus, status.trim().toUpperCase());
        }
        return toCardPage(photoInfoMapper.selectPage(page, wrapper));
    }

    @Override
    public IPage<PhotoCardVO> listPublishedByUid(String uid, Integer current, Integer pageSize) {
        User author = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUid, uid));
        if (author == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        int safeSize = Math.min(pageSize == null ? 10 : pageSize, 100);
        Page<PhotoInfo> page = new Page<>(current == null ? 1 : current, safeSize);
        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper<PhotoInfo>()
                .eq(PhotoInfo::getUserId, author.getId())
                .eq(PhotoInfo::getStatus, PhotoStatus.PUBLISHED.name())
                .orderByDesc(PhotoInfo::getCreateTime);
        return toCardPage(photoInfoMapper.selectPage(page, wrapper));
    }

    @Override
    public PhotoDetailVO getDetail(Long photoId, Long viewerUserId) {
        PhotoInfo photo = photoInfoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        }
        if (!canView(photo, viewerUserId)) {
            // 对未发布作品统一 404，避免泄露资源存在性
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        }
        return photoAssembler.toDetail(photo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PhotoDetailVO update(Long photoId, Long userId, PhotoRequests.Update request) {
        PhotoInfo photo = requireOwnedPhoto(photoId, userId);
        // 已发布作品允许改元数据；若需重新审核可后续扩展为改后回 PENDING
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "标题不能为空");
            }
            photo.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            photo.setDescription(request.getDescription().trim());
        }
        if (request.getLocation() != null) {
            photo.setLocation(request.getLocation().trim());
        }
        if (request.getCategoryId() != null) {
            boolean exists = photoCategoryMapper.exists(new LambdaQueryWrapper<PhotoCategory>()
                    .eq(PhotoCategory::getId, request.getCategoryId()));
            if (!exists) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分类不存在");
            }
            photo.setCategory_id(request.getCategoryId());
        }
        photoInfoMapper.updateById(photo);

        if (request.getTags() != null) {
            photoTagRelationMapper.delete(new LambdaQueryWrapper<PhotoTagRelation>()
                    .eq(PhotoTagRelation::getPhotoId, photoId));
            request.getTags().stream().map(String::trim).filter(this::hasText).distinct().limit(20)
                    .forEach(name -> {
                        PhotoTag tag = photoTagService.getOrCreate(name);
                        photoTagRelationMapper.insert(new PhotoTagRelation(photoId, tag.getId()));
                    });
        }
        return photoAssembler.toDetail(photo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long photoId, Long userId) {
        requireOwnedPhoto(photoId, userId);
        photoTagRelationMapper.delete(new LambdaQueryWrapper<PhotoTagRelation>()
                .eq(PhotoTagRelation::getPhotoId, photoId));
        photoInfoMapper.deleteById(photoId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PhotoDetailVO submit(Long photoId, Long userId) {
        PhotoInfo photo = requireOwnedPhoto(photoId, userId);
        String status = photo.getStatus();
        if (!PhotoStatus.DRAFT.name().equals(status) && !PhotoStatus.REJECTED.name().equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅草稿或驳回作品可提交审核");
        }
        photo.setStatus(PhotoStatus.PENDING.name());
        photo.setRejectReason(null);
        photoInfoMapper.updateById(photo);
        return photoAssembler.toDetail(photo);
    }

    /** 公开列表查询：强制 PUBLISHED */
    private IPage<PhotoCardVO> queryPublishedCards(PhotoRequests.Search request) {
        Page<PhotoInfo> page = new Page<>(request.getCurrent(), request.safePageSize());
        LambdaQueryWrapper<PhotoInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhotoInfo::getStatus, PhotoStatus.PUBLISHED.name());

        if (hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(query -> query.like(PhotoInfo::getTitle, keyword)
                    .or().like(PhotoInfo::getDescription, keyword)
                    .or().like(PhotoInfo::getLocation, keyword));
        }
        wrapper.eq(request.getAuthorId() != null, PhotoInfo::getUserId, request.getAuthorId());
        if (hasText(request.getAuthor())) {
            List<Long> authorIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getUsername, request.getAuthor().trim()))
                    .stream().map(User::getId).toList();
            applyIdFilter(wrapper, PhotoInfo::getUserId, authorIds);
        }
        wrapper.eq(request.getCategoryId() != null, PhotoInfo::getCategory_id, request.getCategoryId());
        wrapper.like(hasText(request.getLocation()), PhotoInfo::getLocation,
                hasText(request.getLocation()) ? request.getLocation().trim() : null);
        wrapper.like(hasText(request.getCameraBody()), PhotoInfo::getCameraBody,
                hasText(request.getCameraBody()) ? request.getCameraBody().trim() : null);
        wrapper.ge(request.getIsoMin() != null, PhotoInfo::getIso, request.getIsoMin());
        wrapper.le(request.getIsoMax() != null, PhotoInfo::getIso, request.getIsoMax());
        wrapper.ge(request.getShootFrom() != null, PhotoInfo::getShoot_date, request.getShootFrom());
        wrapper.le(request.getShootTo() != null, PhotoInfo::getShoot_date, request.getShootTo());

        Set<Long> tagIds = resolveTagIds(request);
        if (tagIds != null) {
            List<Long> photoIds = tagIds.isEmpty() ? List.of() :
                    photoTagRelationMapper.selectList(new LambdaQueryWrapper<PhotoTagRelation>()
                                    .in(PhotoTagRelation::getTagId, tagIds))
                            .stream().map(PhotoTagRelation::getPhotoId).distinct().toList();
            applyIdFilter(wrapper, PhotoInfo::getId, photoIds);
        }

        String sort = request.getSort();
        if ("oldest".equalsIgnoreCase(sort)) {
            wrapper.orderByAsc(PhotoInfo::getCreateTime);
        } else if (sort == null || "latest".equalsIgnoreCase(sort) || "hot".equalsIgnoreCase(sort)) {
            // hot 暂与 latest 相同，点赞落地后再按热度排序
            wrapper.orderByDesc(PhotoInfo::getCreateTime);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序方式仅支持 latest、hot 或 oldest");
        }
        return toCardPage(photoInfoMapper.selectPage(page, wrapper));
    }

    /**
     * 可见性：已发布对所有人可见；其余仅作者或管理员可见。
     */
    private boolean canView(PhotoInfo photo, Long viewerUserId) {
        if (PhotoStatus.PUBLISHED.name().equals(photo.getStatus())) {
            return true;
        }
        if (viewerUserId != null && viewerUserId.equals(photo.getUserId())) {
            return true;
        }
        return isAdmin(viewerUserId);
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private void fillExif(PhotoInfo photo, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory mainDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null) {
                String cameraBody = mainDirectory == null ? null : mainDirectory.getString(ExifIFD0Directory.TAG_MODEL);
                photo.setCameraBody(cameraBody != null ? cameraBody : "null");
                String lens = directory.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
                photo.setLens(lens != null ? lens : "null");
                String aperture = directory.getDescription(ExifSubIFDDirectory.TAG_FNUMBER);
                photo.setAperture(aperture != null ? aperture : "null");
                String shutter = directory.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                photo.setShutterSpeed(shutter != null ? shutter : "null");
                String focalLength = directory.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                photo.setFocalLength(focalLength != null ? focalLength : "null");
                photo.setIso(directory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    photo.setShoot_date(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
            } else {
                photo.setCameraBody("null");
                photo.setLens("null");
                photo.setAperture("null");
                photo.setShutterSpeed("null");
                photo.setFocalLength("null");
                photo.setIso(null);
                photo.setShoot_date(null);
            }
        } catch (Exception e) {
            log.warn("解析 Exif 失败: {}", e.getMessage());
        }
    }

    private String persistFile(MultipartFile file) {
        try {
            String uploadDir = uploadProperties.getDir();
            File dir = new File(uploadDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法创建上传目录");
            }
            String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            String fileName = System.currentTimeMillis() + "_" + original.replaceAll("[\\\\/]+", "_");
            file.transferTo(new File(dir, fileName));
            return fileName;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存上传文件失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private PhotoInfo requireOwnedPhoto(Long photoId, Long userId) {
        requireUserId(userId);
        PhotoInfo photo = photoInfoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        }
        if (!userId.equals(photo.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该作品");
        }
        return photo;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录");
        }
    }

    private void validateStatus(String status) {
        try {
            PhotoStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非法的作品状态");
        }
    }

    private Set<Long> resolveTagIds(PhotoRequests.Search request) {
        if (request.getTagId() != null) {
            return Set.of(request.getTagId());
        }
        if (hasText(request.getTag())) {
            return photoTagMapper.selectList(new LambdaQueryWrapper<PhotoTag>()
                            .like(PhotoTag::getName, request.getTag().trim()))
                    .stream().map(PhotoTag::getId).collect(Collectors.toSet());
        }
        return null;
    }

    private <T> void applyIdFilter(LambdaQueryWrapper<PhotoInfo> wrapper,
                                   SFunction<PhotoInfo, T> column,
                                   Collection<T> ids) {
        if (ids.isEmpty()) {
            wrapper.eq(PhotoInfo::getId, -1L);
        } else {
            wrapper.in(column, ids);
        }
    }

    private IPage<PhotoCardVO> toCardPage(IPage<PhotoInfo> source) {
        Page<PhotoCardVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setPages(source.getPages());
        result.setRecords(source.getRecords().stream().map(photoAssembler::toCard).toList());
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

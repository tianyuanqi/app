package com.yuanqi.app.photo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.http.StrongEtag;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import com.yuanqi.app.photo.vo.MediaViews;
import com.yuanqi.app.photo.vo.WorkViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

@Service
public class MediaService {
    private final MediaAssetMapper mapper;
    private final StoragePort storage;
    private final PublicIdGenerator ids;
    private final Clock clock;
    private final AccountMapper accountMapper;
    private final MediaCleanupService cleanup;
    private final TransactionTemplate transactions;

    public MediaService(MediaAssetMapper mapper, StoragePort storage, PublicIdGenerator ids, Clock clock,
                        AccountMapper accountMapper, MediaCleanupService cleanup,
                        PlatformTransactionManager transactionManager) {
        this.mapper = mapper; this.storage = storage; this.ids = ids; this.clock = clock;
        this.accountMapper = accountMapper; this.cleanup = cleanup;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public MediaViews.Processing upload(Long accountId, String clientUploadId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_CONTENT);
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        MediaAsset existing = mapper.selectOne(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getOwnerAccountId, accountId).eq(MediaAsset::getClientUploadId, clientUploadId));
        if (existing != null) return view(existing);
        String mediaId = ids.next();
        String staging;
        try { staging = storage.stage(mediaId, file.getInputStream()); }
        catch (IOException e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); }
        try {
            MediaViews.Processing created = transactions.execute(status ->
                    create(accountId, clientUploadId, file.getSize(), mediaId, staging));
            if (created == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            return created;
        }
        catch (RuntimeException e) {
            try { storage.delete(staging); }
            catch (IOException cleanupFailure) { cleanup.enqueueStorage(staging, "UPLOAD_COMPENSATION", now()); }
            MediaAsset raced = mapper.selectOne(new LambdaQueryWrapper<MediaAsset>()
                    .eq(MediaAsset::getOwnerAccountId, accountId).eq(MediaAsset::getClientUploadId, clientUploadId));
            if (raced != null) return view(raced);
            throw e;
        }
    }

    private MediaViews.Processing create(Long accountId, String clientUploadId, long size,
                                         String mediaId, String staging) {
        LocalDateTime now = now();
        MediaAsset asset = new MediaAsset(); asset.setMediaId(mediaId); asset.setClientUploadId(clientUploadId);
        asset.setOwnerAccountId(accountId); asset.setPurpose("PHOTO"); asset.setOriginalStorageKey(staging);
        asset.setByteSize(size); asset.setStatus("PROCESSING"); asset.setRetryable(false);
        asset.setRowVersion(0L); asset.setCreatedAt(now); asset.setUpdatedAt(now); mapper.insert(asset);
        return view(asset);
    }

    @Transactional(readOnly = true)
    public MediaViews.Processing get(Long accountId, String mediaId) { return view(owned(mediaId, accountId)); }

    public MediaAsset owned(String mediaId, Long accountId) {
        MediaAsset asset = mapper.findByPublicId(mediaId);
        if (asset == null || !accountId.equals(asset.getOwnerAccountId()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return asset;
    }

    @Transactional
    public MediaViews.Processing retry(Long accountId, String mediaId, String ifMatch) {
        MediaAsset asset = mapper.findByPublicIdForUpdate(mediaId);
        if (asset == null || !accountId.equals(asset.getOwnerAccountId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        match(ifMatch, asset);
        if (!"FAILED".equals(asset.getStatus()) || !Boolean.TRUE.equals(asset.getRetryable())
                || asset.getRetryUntil() == null || !asset.getRetryUntil().isAfter(now()))
            throw new BusinessException(ErrorCode.STATE_CONFLICT);
        asset.setStatus("PROCESSING"); asset.setFailureCode(null); asset.setRetryable(false);
        asset.setUpdatedAt(now()); asset.setRowVersion(asset.getRowVersion() + 1); mapper.updateById(asset);
        return view(asset);
    }

    @Transactional
    public MediaViews.DeleteResult delete(Long accountId, String mediaId, String ifMatch) {
        MediaAsset asset = mapper.findByPublicIdForUpdate(mediaId);
        if (asset == null || !accountId.equals(asset.getOwnerAccountId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        match(ifMatch, asset);
        if (mapper.isReferenced(asset.getId())) throw new BusinessException(ErrorCode.STATE_CONFLICT);
        cleanup.logicalDelete(asset, "OWNER_MEDIA_DELETE");
        return new MediaViews.DeleteResult(asset.getMediaId(), true, asset.getStatus());
    }

    @Transactional(readOnly = true)
    public MediaAsset readableWeb(Long accountId, String mediaId) {
        MediaAsset asset = mapper.findByPublicId(mediaId);
        if (asset == null || !"READY".equals(asset.getStatus()) || asset.getWebStorageKey() == null)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!mapper.isPublicWeb(asset.getId()) && !asset.getOwnerAccountId().equals(accountId)) {
            Account viewer = accountId == null ? null : accountMapper.selectById(accountId);
            boolean pendingReviewAccess = viewer != null && "ADMIN".equals(viewer.getRole())
                    && mapper.isPendingReviewWeb(asset.getId());
            if (!pendingReviewAccess) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return asset;
    }

    public MediaViews.Processing view(MediaAsset a) {
        MediaViews.WebMedia web = "READY".equals(a.getStatus()) ? new MediaViews.WebMedia(a.getMediaId(),
                mapper.isPublicWeb(a.getId()) ? "PUBLIC_URL" : "BEARER_FETCH",
                "/api/v1/media/" + a.getMediaId() + "/web", "image/jpeg", a.getWidth(), a.getHeight(), tag(a)) : null;
        MediaViews.Failure failure = a.getFailureCode() == null ? null
                : new MediaViews.Failure(a.getFailureCode(), "媒体处理未成功");
        WorkViews.PhotoParameters exifCandidate = exifCandidate(a);
        List<MediaViews.Warning> warnings = ExifExtractor.decodeWarnings(a.getExifWarningCodes()).stream()
                .map(w -> new MediaViews.Warning(w.code(), w.field(), w.message())).toList();
        return new MediaViews.Processing(a.getMediaId(), a.getClientUploadId(), a.getStatus(), a.getByteSize(),
                a.getWidth(), a.getHeight(), web, exifCandidate, warnings, failure,
                Boolean.TRUE.equals(a.getRetryable()), a.getRetryUntil() == null ? null : a.getRetryUntil().atOffset(ZoneOffset.UTC), tag(a));
    }

    private WorkViews.PhotoParameters exifCandidate(MediaAsset a) {
        if (a.getExifCaptureTime() == null && a.getExifCameraBody() == null && a.getExifLens() == null
                && a.getExifFocalLength() == null && a.getExifAperture() == null
                && a.getExifShutterSpeed() == null && a.getExifIsoValue() == null) return null;
        return new WorkViews.PhotoParameters(a.getExifCaptureTime() == null ? null
                : a.getExifCaptureTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),
                a.getExifCameraBody(), a.getExifLens(), a.getExifFocalLength(), a.getExifAperture(),
                a.getExifShutterSpeed(), a.getExifIsoValue());
    }

    public String tag(MediaAsset asset) { return "\"media-" + asset.getRowVersion() + "\""; }
    private void match(String value, MediaAsset asset) {
        StrongEtag.requireMatch(value, tag(asset));
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}

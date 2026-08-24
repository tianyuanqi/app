package com.yuanqi.app.photo.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.service.MediaService;
import com.yuanqi.app.photo.service.MediaStorage;
import com.yuanqi.app.photo.vo.MediaViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;
import com.yuanqi.app.common.idempotency.IdempotencyService;
import java.io.IOException;
import java.util.Map;

@Tag(name = "媒体")
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService service;
    private final MediaStorage storage;
    private final IdempotencyService idempotency;
    public MediaController(MediaService service, MediaStorage storage, IdempotencyService idempotency) { this.service = service; this.storage = storage; this.idempotency=idempotency; }

    @Operation(summary = "上传作品原图", security = @SecurityRequirement(name = "Authorization"))
    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<MediaViews.Processing>> upload(@RequestParam String clientUploadId,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestPart("file") MultipartFile file) throws IOException {
        Map<String,Object> digest=Map.of("clientUploadId",clientUploadId,"fileSha256",IdempotencyService.sha256(file.getBytes()),"fileBytes",file.getSize());
        return idempotency.execute(subject(),"POST","/api/v1/media/photos",key,digest,MediaViews.Processing.class,()->{MediaViews.Processing view=service.upload(UserContext.getUserId(),clientUploadId,file);return ResponseEntity.accepted().header(HttpHeaders.ETAG,view.versionTag()).header(HttpHeaders.LOCATION,"/api/v1/media/photos/"+view.mediaId()).body(Result.success(view));});
    }

    @Operation(summary = "读取媒体处理状态", security = @SecurityRequirement(name = "Authorization"))
    @GetMapping("/photos/{mediaId}")
    public ResponseEntity<Result<MediaViews.Processing>> get(@PathVariable String mediaId) {
        MediaViews.Processing view = service.get(UserContext.getUserId(), mediaId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.versionTag()).body(Result.success(view));
    }

    @Operation(summary = "重试可恢复媒体处理", security = @SecurityRequirement(name = "Authorization"))
    @PostMapping("/photos/{mediaId}/retry")
    public ResponseEntity<Result<MediaViews.Processing>> retry(@PathVariable String mediaId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return idempotency.execute(subject(),"POST","/api/v1/media/photos/{mediaId}/retry",key,Map.of("mediaId",mediaId,"ifMatch",String.valueOf(ifMatch)),MediaViews.Processing.class,()->{MediaViews.Processing view=service.retry(UserContext.getUserId(),mediaId,ifMatch);return ResponseEntity.accepted().header(HttpHeaders.ETAG,view.versionTag()).body(Result.success(view));});
    }

    @Operation(summary = "删除未被作品引用的媒体", security = @SecurityRequirement(name = "Authorization"))
    @DeleteMapping("/photos/{mediaId}")
    public Result<MediaViews.DeleteResult> delete(@PathVariable String mediaId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return Result.success(service.delete(UserContext.getUserId(), mediaId, ifMatch));
    }

    @Operation(summary = "读取 Web 展示版本；永不提供原图")
    @GetMapping("/{mediaId}/web")
    public ResponseEntity<FileSystemResource> web(@PathVariable String mediaId) {
        MediaAsset asset = service.readableWeb(UserContext.getUserId(), mediaId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .eTag(service.tag(asset)).cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(new FileSystemResource(storage.safe(asset.getWebStorageKey())));
    }
    private String subject(){return UserContext.getUid();}
}

package com.yuanqi.app.photo.service;

import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Service
public class MediaProcessor {
    private final MediaAssetMapper mapper;
    private final StoragePort storage;
    private final Clock clock;
    private final ExifExtractor exifExtractor;
    private final TransactionTemplate transactions;
    private final MediaCleanupService cleanup;

    public MediaProcessor(MediaAssetMapper mapper, StoragePort storage, Clock clock, ExifExtractor exifExtractor,
                          PlatformTransactionManager transactionManager, MediaCleanupService cleanup) {
        this.mapper = mapper; this.storage = storage; this.clock = clock; this.exifExtractor = exifExtractor;
        this.transactions = new TransactionTemplate(transactionManager); this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${app.media.worker-delay-ms:1000}")
    public void processPending() {
        List<MediaAsset> assets = mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getStatus, "PROCESSING").orderByAsc(MediaAsset::getCreatedAt).last("LIMIT 10"));
        assets.forEach(a -> process(a.getMediaId()));
    }

    public void process(String mediaId) {
        transactions.executeWithoutResult(status -> processInTransaction(mediaId));
    }

    private void processInTransaction(String mediaId) {
        MediaAsset asset = mapper.findByPublicIdForUpdate(mediaId);
        if (asset == null || !"PROCESSING".equals(asset.getStatus())) return;
        String staging = asset.getOriginalStorageKey();
        try {
            Decoded decoded = decode(staging);
            ExifExtractor.Result exif = "PHOTO".equals(asset.getPurpose())
                    ? exifExtractor.extract(storage.safe(staging), clock)
                    : new ExifExtractor.Result(null, List.of());
            String originalKey = storage.originalKey(asset.getMediaId(), decoded.extension());
            storage.move(staging, originalKey);
            asset.setOriginalStorageKey(originalKey);
            String webKey = storage.webKey(asset.getMediaId());
            writeWeb(decoded.image(), webKey);
            asset.setWebStorageKey(webKey);
            asset.setSha256(sha256(originalKey)); asset.setMimeType(decoded.mimeType());
            asset.setWidth(decoded.image().getWidth()); asset.setHeight(decoded.image().getHeight());
            asset.setFrameCount(decoded.frames()); asset.setStatus("READY"); asset.setFailureCode(null);
            asset.setRetryable(false); asset.setRetryUntil(null);
            applyExif(asset, exif);
        } catch (MediaValidationException e) {
            asset.setStatus("FAILED"); asset.setFailureCode(e.code); asset.setRetryable(false);
            try { storage.delete(staging); }
            catch (IOException cleanupFailure) { cleanup.enqueueStorage(staging, "VALIDATION_COMPENSATION", now()); }
            asset.setOriginalStorageKey(null);
        } catch (Exception e) {
            asset.setStatus("FAILED"); asset.setFailureCode("PROCESSING_FAILED"); asset.setRetryable(true);
            asset.setRetryUntil(now().plusHours(24));
        }
        asset.setUpdatedAt(now()); asset.setRowVersion(asset.getRowVersion() + 1); mapper.updateById(asset);
    }

    private Decoded decode(String key) throws IOException {
        Path source = storage.safe(key);
        if (!Files.isRegularFile(source)) {
            // 暂存对象缺失属于存储/处理故障，保留重试窗口；不能误报为用户上传内容无效。
            throw new IOException("staged media unavailable");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new MediaValidationException("INVALID_CONTENT");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new MediaValidationException("UNSUPPORTED_FORMAT");
            ImageReader reader = readers.next();
            try {
                // getNumImages(true) 需要允许 Reader 在输入中回溯；seekForwardOnly=true 会使合法 PNG
                // 在帧数检查时抛出 IllegalStateException，并被误判为可重试处理失败。
                reader.setInput(input, false, false);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.contains("jpeg") || format.equals("jpg") || format.equals("png") || format.equals("webp")))
                    throw new MediaValidationException("UNSUPPORTED_FORMAT");
                int frames;
                try { frames = reader.getNumImages(true); } catch (IOException e) { frames = 1; }
                if (frames != 1) throw new MediaValidationException("DYNAMIC_IMAGE");
                BufferedImage image = reader.read(0);
                if (image == null) throw new MediaValidationException("INVALID_CONTENT");
                int shortSide = Math.min(image.getWidth(), image.getHeight());
                int longSide = Math.max(image.getWidth(), image.getHeight());
                if (shortSide < 320 || longSide > 12000) throw new MediaValidationException("DIMENSION_OUT_OF_RANGE");
                String mime = format.equals("png") ? "image/png" : format.equals("webp") ? "image/webp" : "image/jpeg";
                String ext = format.equals("png") ? "png" : format.equals("webp") ? "webp" : "jpg";
                return new Decoded(image, mime, ext, frames);
            } finally { reader.dispose(); }
        }
    }

    private void writeWeb(BufferedImage original, String key) throws IOException {
        int width = original.getWidth(), height = original.getHeight();
        double scale = Math.min(1d, 2400d / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally { graphics.dispose(); }
        try (var target = storage.create(key)) {
            if (!ImageIO.write(output, "jpeg", target)) throw new IOException("JPEG writer unavailable");
        }
    }

    private String sha256(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = storage.open(key)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void applyExif(MediaAsset asset, ExifExtractor.Result result) {
        ExifExtractor.Candidate candidate = result.candidate();
        asset.setExifCaptureTime(candidate == null ? null : candidate.captureTime());
        asset.setExifCameraBody(candidate == null ? null : candidate.cameraBody());
        asset.setExifLens(candidate == null ? null : candidate.lens());
        asset.setExifFocalLength(candidate == null ? null : candidate.focalLength());
        asset.setExifAperture(candidate == null ? null : candidate.aperture());
        asset.setExifShutterSpeed(candidate == null ? null : candidate.shutterSpeed());
        asset.setExifIsoValue(candidate == null ? null : candidate.iso());
        asset.setExifWarningCodes(ExifExtractor.encodeWarnings(result.warnings()));
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private record Decoded(BufferedImage image, String mimeType, String extension, int frames) {}
    private static class MediaValidationException extends IOException {
        private final String code;
        private MediaValidationException(String code) { this.code = code; }
    }
}

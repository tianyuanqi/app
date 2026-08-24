package com.yuanqi.app.photo.service;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import com.yuanqi.app.photo.vo.MediaViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FE-V1-RUNTIME-002：不使用 READY Seed 的真实解码与 Web 衍生处理集成证据。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaProcessorIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "2400px-media-processor-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", MEDIA_ROOT::toString);
        registry.add("app.media.worker-delay-ms", () -> "3600000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MediaService media;
    @Autowired MediaProcessor processor;
    @Autowired MediaStorage storage;
    @Autowired MediaAssetMapper mapper;

    @Test void 有效1200方形PNG从PROCESSING进入READY并生成JPEG衍生图() throws Exception {
        long owner = account("uid_png_ready");
        byte[] png = png(1200, 1200);
        MediaViews.Processing accepted = media.upload(owner, UUID.randomUUID().toString(),
                new MockMultipartFile("file", "fixture.png", "image/png", png));
        assertThat(accepted.status()).isEqualTo("PROCESSING");

        processor.process(accepted.mediaId());
        MediaViews.Processing ready = media.get(owner, accepted.mediaId());
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.width()).isEqualTo(1200);
        assertThat(ready.height()).isEqualTo(1200);
        assertThat(ready.web()).isNotNull();
        assertThat(ready.web().mimeType()).isEqualTo("image/jpeg");
        assertThat(ready.web().url()).isEqualTo("/api/v1/media/" + ready.mediaId() + "/web");
        assertThat(ready.web().url()).doesNotContain("original", "staging", MEDIA_ROOT.toString());
        MediaAsset stored = mapper.findByPublicId(ready.mediaId());
        assertThat(stored.getOriginalStorageKey()).startsWith("original/");
        assertThat(stored.getWebStorageKey()).startsWith("web/");
        assertThat(Files.readAllBytes(storage.safe(stored.getWebStorageKey())))
                .startsWith((byte) 0xff, (byte) 0xd8);
    }

    @Test void 损坏图片确定失败且不可重试并清理暂存文件() {
        long owner = account("uid_png_invalid");
        MediaViews.Processing accepted = media.upload(owner, UUID.randomUUID().toString(),
                new MockMultipartFile("file", "broken.png", "image/png", new byte[]{1, 2, 3, 4}));
        String staging = mapper.findByPublicId(accepted.mediaId()).getOriginalStorageKey();

        processor.process(accepted.mediaId());
        MediaViews.Processing failed = media.get(owner, accepted.mediaId());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failure().code()).isEqualTo("UNSUPPORTED_FORMAT");
        assertThat(failed.retryable()).isFalse();
        assertThat(Files.exists(storage.safe(staging))).isFalse();
        assertError(ErrorCode.STATE_CONFLICT,
                () -> media.retry(owner, failed.mediaId(), failed.versionTag()));
    }

    @Test void 临时处理失败可重试且ClientUploadId保持单一Asset() throws Exception {
        long owner = account("uid_png_retry");
        String clientUploadId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile("file", "fixture.png", "image/png", png(1200, 1200));
        MediaViews.Processing accepted = media.upload(owner, clientUploadId, file);
        MediaViews.Processing replay = media.upload(owner, clientUploadId,
                new MockMultipartFile("file", "other.png", "image/png", png(640, 640)));
        assertThat(replay.mediaId()).isEqualTo(accepted.mediaId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND client_upload_id=?",
                Integer.class, owner, clientUploadId)).isEqualTo(1);

        MediaAsset asset = mapper.findByPublicId(accepted.mediaId());
        storage.delete(asset.getOriginalStorageKey());
        processor.process(accepted.mediaId());
        MediaViews.Processing failed = media.get(owner, accepted.mediaId());
        assertThat(failed.failure().code()).isEqualTo("PROCESSING_FAILED");
        assertThat(failed.retryable()).isTrue();
        assertThat(failed.retryUntil()).isNotNull();
        MediaViews.Processing retrying = media.retry(owner, failed.mediaId(), failed.versionTag());
        assertThat(retrying.mediaId()).isEqualTo(failed.mediaId());
        assertThat(retrying.status()).isEqualTo("PROCESSING");
        assertThat(retrying.versionTag()).isNotEqualTo(failed.versionTag());
    }

    private long account(String uid) {
        String email = uid + "@example.invalid";
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) " +
                        "VALUES(?,?,?,?, 'USER','ACTIVE',0,NOW(6),NOW(6))", uid, email, email, "test-hash");
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,NOW(6))", id, uid);
        return id;
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 100, 180, 220));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }
}

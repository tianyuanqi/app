package com.yuanqi.app.photo.service;

import com.yuanqi.app.AppApplication;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.moderation.service.ReviewService;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.vo.PublicPhotoViews;
import com.yuanqi.app.photo.vo.WorkViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {AppApplication.class, ExifTimeRulesIntegrationTest.FixedClockConfig.class})
@ActiveProfiles("test")
@Transactional
class ExifTimeRulesIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-29T04:00:00Z");
    private static final Path MEDIA_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "2400px-exif-time-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", MEDIA_ROOT::toString);
        registry.add("app.media.worker-delay-ms", () -> "3600000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired WorkService works;
    @Autowired ReviewService reviews;
    @Autowired PublicPhotoService publicPhotos;
    @Autowired Clock clock;

    @Test void 两张媒体EXIF独立且手工覆盖与清空不污染另一张() {
        long owner = account("uid_exif_isolation", "USER");
        String first = media(owner, "media_exif_first", "2020-01-02 03:04:05", "Camera A", "Lens A");
        String second = media(owner, "media_exif_second", "2021-02-03 04:05:06", "Camera B", "Lens B");
        WorkRequests.Draft initial = draft(List.of(first, second), null);
        WorkViews.AuthorWork created = works.create(owner, initial);
        assertThat(created.workingRevision().media()).extracting(x -> x.parameters().cameraBody())
                .containsExactly("Camera A", "Camera B");

        WorkRequests.PhotoParameters override = new WorkRequests.PhotoParameters(
                OffsetDateTime.parse("2022-03-04T05:06:07+08:00"), "Manual A", "Manual Lens", "50mm",
                "f/2.0", "1/250s", "200");
        WorkViews.AuthorWork saved = works.updateDraft(owner, created.summary().workId(),
                created.summary().versionTag(), draft(List.of(first, second),
                        List.of(new WorkRequests.MediaParameters(first, override))));
        assertThat(saved.workingRevision().media().get(0).parameters().cameraBody()).isEqualTo("Manual A");
        assertThat(saved.workingRevision().media().get(1).parameters().cameraBody()).isEqualTo("Camera B");

        WorkRequests.PhotoParameters clear = new WorkRequests.PhotoParameters(null, null, null, null, null, null, null);
        WorkViews.AuthorWork cleared = works.updateDraft(owner, saved.summary().workId(), saved.summary().versionTag(),
                draft(List.of(first, second), List.of(new WorkRequests.MediaParameters(first, clear))));
        assertThat(cleared.workingRevision().media().get(0).parameters())
                .usingRecursiveComparison().isEqualTo(new WorkViews.PhotoParameters(null, null, null, null, null, null, null));
        assertThat(cleared.workingRevision().media().get(1).parameters().cameraBody()).isEqualTo("Camera B");
    }

    @Test void 上海当前与过去可保存而未来长度和控制字符被拒绝() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        long owner = account("uid_exif_time", "USER");
        String media = media(owner, "media_exif_time", null, null, null);
        WorkViews.AuthorWork created = works.create(owner, draft(List.of(media), null));

        WorkRequests.PhotoParameters current = new WorkRequests.PhotoParameters(
                OffsetDateTime.parse("2026-08-29T12:00:00+08:00"), "相机", null, null, null, null, null);
        WorkViews.AuthorWork equal = works.updateDraft(owner, created.summary().workId(), created.summary().versionTag(),
                draft(List.of(media), List.of(new WorkRequests.MediaParameters(media, current))));
        assertThat(equal.workingRevision().media().get(0).parameters().captureTime().toString())
                .isEqualTo("2026-08-29T12:00+08:00");

        WorkRequests.PhotoParameters future = new WorkRequests.PhotoParameters(
                OffsetDateTime.parse("2026-08-29T12:00:00.000001+08:00"), null, null, null, null, null, null);
        assertValidation(() -> works.updateDraft(owner, equal.summary().workId(), equal.summary().versionTag(),
                draft(List.of(media), List.of(new WorkRequests.MediaParameters(media, future)))));

        WorkRequests.PhotoParameters tooLong = new WorkRequests.PhotoParameters(null, "相".repeat(101), null,
                null, null, null, null);
        assertValidation(() -> works.updateDraft(owner, equal.summary().workId(), equal.summary().versionTag(),
                draft(List.of(media), List.of(new WorkRequests.MediaParameters(media, tooLong)))));
        WorkRequests.PhotoParameters control = new WorkRequests.PhotoParameters(null, null, "Lens\nInjected",
                null, null, null, null);
        assertValidation(() -> works.updateDraft(owner, equal.summary().workId(), equal.summary().versionTag(),
                draft(List.of(media), List.of(new WorkRequests.MediaParameters(media, control)))));
    }

    @Test void 作者审核和公开详情返回同一逐图上海时间参数() {
        long owner = account("uid_exif_projection_owner", "USER");
        long admin = account("uid_exif_projection_admin", "ADMIN");
        String media = media(owner, "media_exif_projection", "2020-01-02 03:04:05", "Projection Camera", "Projection Lens");
        WorkViews.AuthorWork created = works.create(owner, draft(List.of(media), null));
        WorkViews.AuthorWork pending = works.submit(owner, created.summary().workId(), created.summary().versionTag());
        var target = reviews.target(admin, created.summary().workId(), pending.workingRevision().revisionId());
        assertThat(target.targetRevision().media().get(0).parameters().captureTime().toString())
                .isEqualTo("2020-01-02T03:04:05+08:00");
        reviews.approve(admin, created.summary().workId(), pending.workingRevision().revisionId(),
                pending.summary().versionTag());
        PublicPhotoViews.Detail detail = publicPhotos.detail(null, created.summary().workId());
        assertThat(detail.media().get(0).parameters().cameraBody()).isEqualTo("Projection Camera");
        assertThat(detail.media().get(0).parameters().captureTime().toString())
                .isEqualTo("2020-01-02T03:04:05+08:00");
    }

    private WorkRequests.Draft draft(List<String> mediaIds, List<WorkRequests.MediaParameters> parameters) {
        return new WorkRequests.Draft(null, null, null, null, List.of(), mediaIds, parameters);
    }

    private long account(String uid, String role) {
        String email = uid + "@example.invalid";
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                uid, email, email, "test-hash", role);
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,UTC_TIMESTAMP(6))", id, uid);
        return id;
    }

    private String media(long owner, String mediaId, String capture, String camera, String lens) {
        jdbc.update("INSERT INTO media_asset(media_id,client_upload_id,owner_account_id,purpose,original_storage_key,web_storage_key,mime_type,byte_size,width,height,frame_count,exif_capture_time,exif_camera_body,exif_lens,exif_focal_length,exif_aperture,exif_shutter_speed,exif_iso_value,status,row_version,created_at,updated_at) " +
                        "VALUES(?,?,?,'PHOTO',?,?,'image/jpeg',1024,1200,1200,1,?,?,?,?,?,?,?,'READY',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                mediaId, UUID.randomUUID().toString(), owner, "original/" + mediaId + ".jpg", "web/" + mediaId + ".jpg",
                capture, camera, lens, camera == null ? null : "35 mm", camera == null ? null : "f/1.8",
                camera == null ? null : "1/125 sec", camera == null ? null : "ISO 100");
        return mediaId;
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}

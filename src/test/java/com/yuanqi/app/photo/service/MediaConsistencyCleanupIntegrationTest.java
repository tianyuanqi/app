package com.yuanqi.app.photo.service;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MediaConsistencyCleanupIntegrationTest.ControlledInfrastructure.class)
class MediaConsistencyCleanupIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "2400px-media-cleanup-" + UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", MEDIA_ROOT::toString);
        registry.add("app.media.worker-delay-ms", () -> "3600000");
        registry.add("app.media.cleanup-delay-ms", () -> "3600000");
        registry.add("app.media.reconcile-delay-ms", () -> "3600000");
        registry.add("app.media.cleanup-max-attempts", () -> "2");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MediaAssetMapper assets;
    @Autowired MediaService media;
    @Autowired MediaProcessor processor;
    @Autowired MediaCleanupWorker worker;
    @Autowired MediaCleanupService cleanup;
    @Autowired MediaConsistencyReconciler reconciler;
    @Autowired FaultStorage storage;
    @Autowired MutableClock clock;

    @BeforeEach void resetFaults() {
        storage.reset(); clock.set(Instant.parse("2026-08-30T02:00:00Z"));
        jdbc.update("DELETE FROM media_cleanup_job"); jdbc.update("DELETE FROM media_consistency_issue");
    }

    @Test void 删除先撤权再由幂等任务物理清理() throws Exception {
        long owner = account("delete"); MediaAsset asset = ready(owner, "delete");
        var deleted = media.delete(owner, asset.getMediaId(), "\"media-0\"");
        assertThat(deleted.status()).isEqualTo("DELETE_PENDING");
        assertThatThrownBy(() -> media.readableWeb(owner, asset.getMediaId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(jobStatus(asset.getId())).isEqualTo("PENDING");

        assertThat(worker.runDue(10)).isEqualTo(1);
        MediaAsset cleaned = assets.selectById(asset.getId());
        assertThat(cleaned.getStatus()).isEqualTo("DELETED");
        assertThat(cleaned.getOriginalStorageKey()).isNull(); assertThat(cleaned.getWebStorageKey()).isNull();
        assertThat(jobStatus(asset.getId())).isEqualTo("DELETED");
        assertThat(worker.runDue(10)).isZero();
    }

    @Test void 临时失败重试成功且过期RUNNING租约可恢复() throws Exception {
        long owner = account("retry"); MediaAsset asset = ready(owner, "retry");
        media.delete(owner, asset.getMediaId(), "\"media-0\""); storage.failNextDeletes(1);
        worker.runDue(1);
        assertThat(jobStatus(asset.getId())).isEqualTo("RETRY");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM media_cleanup_job WHERE media_id=?", Integer.class, asset.getId())).isEqualTo(1);

        clock.advance(Duration.ofMinutes(2)); worker.runDue(1);
        assertThat(jobStatus(asset.getId())).isEqualTo("DELETED");

        MediaAsset recovering = ready(owner, "recovery");
        media.delete(owner, recovering.getMediaId(), "\"media-0\"");
        jdbc.update("UPDATE media_cleanup_job SET status='RUNNING',locked_until=? WHERE media_id=?",
                java.sql.Timestamp.valueOf(cleanup.now().minusMinutes(1)), recovering.getId());
        worker.runDue(1);
        assertThat(jobStatus(recovering.getId())).isEqualTo("DELETED");
    }

    @Test void 重试耗尽进入可观察终态() throws Exception {
        long owner = account("exhaust"); MediaAsset asset = ready(owner, "exhaust");
        media.delete(owner, asset.getMediaId(), "\"media-0\""); storage.failAllDeletes(true);
        worker.runDue(1); clock.advance(Duration.ofMinutes(2)); worker.runDue(1);
        assertThat(jobStatus(asset.getId())).isEqualTo("EXHAUSTED");
        assertThat(jdbc.queryForMap("SELECT reason,attempt_count,next_attempt_at,last_error_category,completed_at " +
                "FROM media_cleanup_job WHERE media_id=?", asset.getId()))
                .containsEntry("reason", "OWNER_MEDIA_DELETE").containsEntry("attempt_count", 2);
    }

    @Test void 并发Runner只领取一次且重复执行稳定() throws Exception {
        long owner = account("concurrent"); MediaAsset asset = ready(owner, "concurrent");
        media.delete(owner, asset.getMediaId(), "\"media-0\"");
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return worker.runDue(1); });
            var second = pool.submit(() -> { start.await(); return worker.runDue(1); });
            start.countDown(); assertThat(first.get() + second.get()).isBetween(1, 2);
        } finally { pool.shutdownNow(); }
        assertThat(jobStatus(asset.getId())).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM media_cleanup_job WHERE media_id=?", Integer.class, asset.getId())).isEqualTo(1);
        assertThat(worker.runDue(2)).isZero();
    }

    @Test void 双向对账识别四类异常且不清理合法或新文件() throws Exception {
        long owner = account("reconcile"); MediaAsset legal = ready(owner, "legal");
        Path newOrphan = storage.safe("staging/recent-orphan.upload"); Files.createDirectories(newOrphan.getParent()); Files.writeString(newOrphan, "new");
        Path oldOrphan = storage.safe("staging/old-orphan.upload"); Files.writeString(oldOrphan, "old");
        Files.setLastModifiedTime(oldOrphan, FileTime.from(clock.instant().minus(Duration.ofHours(25))));
        MediaAsset missing = ready(owner, "missing"); storage.delete(missing.getWebStorageKey());
        MediaAsset duplicate = asset(owner, "duplicate", legal.getOriginalStorageKey(), "web/duplicate.jpg", "READY");
        try (OutputStream out = storage.create(duplicate.getWebStorageKey())) { out.write(2); }
        MediaAsset anomalous = asset(owner, "anomaly", null, null, "READY");

        MediaConsistencyReconciler.Report report = reconciler.reconcile();
        assertThat(report.openIssues()).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForList("SELECT DISTINCT issue_type FROM media_consistency_issue WHERE status='OPEN'", String.class))
                .contains("UNTRACKED_FILE", "DB_REFERENCE_MISSING_FILE", "DUPLICATE_STORAGE_KEY", "STATUS_ANOMALY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_job WHERE storage_key='staging/old-orphan.upload'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_job WHERE storage_key='staging/recent-orphan.upload'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_job WHERE media_id=?", Integer.class, legal.getId())).isZero();
        assertThat(storage.exists(legal.getOriginalStorageKey())).isTrue();
        assertThat(anomalous.getId()).isNotNull();
    }

    @Test void 校验失败的补偿删除失败会登记持久任务且不生成重复Asset() throws Exception {
        long owner = account("compensate"); String clientUpload = UUID.randomUUID().toString();
        storage.failNextDeletes(1);
        var accepted = media.upload(owner, clientUpload,
                new MockMultipartFile("file", "broken.png", "image/png", new byte[]{1, 2, 3}));
        processor.process(accepted.mediaId());
        MediaAsset failed = assets.findByPublicId(accepted.mediaId());
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_job WHERE storage_key LIKE 'staging/%' " +
                "AND reason='VALIDATION_COMPENSATION'", Integer.class)).isGreaterThanOrEqualTo(1);
        var replay = media.upload(owner, clientUpload,
                new MockMultipartFile("file", "other.png", "image/png", new byte[]{4, 5, 6}));
        assertThat(replay.mediaId()).isEqualTo(accepted.mediaId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND client_upload_id=?",
                Integer.class, owner, clientUpload)).isEqualTo(1);
    }

    private String jobStatus(Long id) { return jdbc.queryForObject("SELECT status FROM media_cleanup_job WHERE media_id=?", String.class, id); }
    private long account(String suffix) {
        String uid = "uid_be008_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8);
        String email = uid + "@example.invalid";
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) " +
                "VALUES(?,?,?,?, 'USER','ACTIVE',0,?,?)", uid, email, email, "test-hash", cleanup.now(), cleanup.now());
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,?)", id, uid, cleanup.now());
        return id;
    }
    private MediaAsset ready(long owner, String suffix) throws Exception {
        String id = "m_be008_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MediaAsset asset = asset(owner, id, "original/" + id + ".png", "web/" + id + ".jpg", "READY");
        try (OutputStream out = storage.create(asset.getOriginalStorageKey())) { out.write(1); }
        try (OutputStream out = storage.create(asset.getWebStorageKey())) { out.write(2); }
        return asset;
    }
    private MediaAsset asset(long owner, String id, String original, String web, String status) {
        MediaAsset asset = new MediaAsset(); asset.setMediaId(id); asset.setClientUploadId(UUID.randomUUID().toString());
        asset.setOwnerAccountId(owner); asset.setPurpose("PHOTO"); asset.setOriginalStorageKey(original); asset.setWebStorageKey(web);
        asset.setStatus(status); asset.setRetryable(false); asset.setRowVersion(0L); asset.setCreatedAt(cleanup.now()); asset.setUpdatedAt(cleanup.now());
        assets.insert(asset); return asset;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledInfrastructure {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(Instant.parse("2026-08-30T02:00:00Z")); }
        @Bean @Primary FaultStorage faultStorage(MediaStorage delegate) { return new FaultStorage(delegate); }
    }
    static final class MutableClock extends Clock {
        private volatile Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void set(Instant value) { instant = value; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
    static final class FaultStorage implements StoragePort {
        private final MediaStorage delegate; private final AtomicInteger failedDeletes = new AtomicInteger();
        private volatile boolean failAll;
        FaultStorage(MediaStorage delegate) { this.delegate = delegate; }
        void reset() { failedDeletes.set(0); failAll = false; }
        void failNextDeletes(int count) { failedDeletes.set(count); }
        void failAllDeletes(boolean value) { failAll = value; }
        @Override public String stage(String id, InputStream in) throws IOException { return delegate.stage(id, in); }
        @Override public Path safe(String key) { return delegate.safe(key); }
        @Override public String originalKey(String id, String ext) { return delegate.originalKey(id, ext); }
        @Override public String webKey(String id) { return delegate.webKey(id); }
        @Override public void move(String source, String target) throws IOException { delegate.move(source, target); }
        @Override public void delete(String key) throws IOException {
            if (failAll || failedDeletes.getAndUpdate(value -> Math.max(0, value - 1)) > 0) throw new IOException("injected");
            delegate.delete(key);
        }
        @Override public boolean exists(String key) throws IOException { return delegate.exists(key); }
        @Override public InputStream open(String key) throws IOException { return delegate.open(key); }
        @Override public OutputStream create(String key) throws IOException { return delegate.create(key); }
        @Override public List<StoredObject> list() throws IOException { return delegate.list(); }
    }
}

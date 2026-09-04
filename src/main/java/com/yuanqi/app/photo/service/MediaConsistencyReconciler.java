package com.yuanqi.app.photo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 双向对账仅自动清理超过保护期的无 DB 对象；其余异常只持久记录，不猜测业务归属。 */
@Service
public class MediaConsistencyReconciler {
    private static final Logger log = LoggerFactory.getLogger(MediaConsistencyReconciler.class);
    private final MediaAssetMapper media;
    private final StoragePort storage;
    private final MediaCleanupService cleanup;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public MediaConsistencyReconciler(MediaAssetMapper media, StoragePort storage, MediaCleanupService cleanup,
                                      JdbcTemplate jdbc, Clock clock, PlatformTransactionManager manager) {
        this.media = media; this.storage = storage; this.cleanup = cleanup; this.jdbc = jdbc; this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${app.media.reconcile-delay-ms:3600000}")
    public void scheduled() {
        try { reconcile(); }
        catch (IOException e) { log.warn("媒体存储对账无法完成: category={}", e.getClass().getSimpleName()); }
    }

    /** 非 HTTP 的确定性入口。 */
    public Report reconcile() throws IOException {
        List<MediaAsset> assets = media.selectList(new LambdaQueryWrapper<>());
        List<StoragePort.StoredObject> objects = storage.list();
        Map<String, List<MediaAsset>> owners = new HashMap<>();
        for (MediaAsset asset : assets) {
            add(owners, asset.getOriginalStorageKey(), asset); add(owners, asset.getWebStorageKey(), asset);
        }
        Set<String> actual = new HashSet<>();
        objects.forEach(object -> actual.add(object.key()));
        List<Issue> issues = new ArrayList<>();
        int queued = 0;
        for (StoragePort.StoredObject object : objects) {
            if (!owners.containsKey(object.key())) {
                issues.add(new Issue("UNTRACKED_FILE", null, object.key()));
                if (Duration.between(object.lastModified(), clock.instant()).compareTo(Duration.ofHours(24)) >= 0) {
                    cleanup.enqueueStorage(object.key(), "ORPHAN_RECONCILIATION", cleanup.now()); queued++;
                }
            }
        }
        for (Map.Entry<String, List<MediaAsset>> entry : owners.entrySet()) {
            if (entry.getValue().size() > 1)
                entry.getValue().forEach(asset -> issues.add(new Issue("DUPLICATE_STORAGE_KEY", asset.getId(), entry.getKey())));
            if (!actual.contains(entry.getKey()))
                entry.getValue().forEach(asset -> issues.add(new Issue("DB_REFERENCE_MISSING_FILE", asset.getId(), entry.getKey())));
        }
        for (MediaAsset asset : assets) {
            if (statusAnomaly(asset)) issues.add(new Issue("STATUS_ANOMALY", asset.getId(), null));
            if (staleUnreferenced(asset)) {
                issues.add(new Issue("STALE_UNREFERENCED_MEDIA", asset.getId(), null));
                cleanup.logicalDelete(asset, "STALE_MEDIA_RECONCILIATION"); queued++;
            }
            if ("DELETE_PENDING".equals(asset.getStatus()) && !activeJob(asset.getId())) {
                cleanup.enqueueMedia(asset.getId(), "RECONCILED_DELETE_PENDING", cleanup.now()); queued++;
            }
        }
        persist(issues);
        return new Report(assets.size(), objects.size(), issues.size(), queued);
    }

    private boolean statusAnomaly(MediaAsset asset) {
        return ("READY".equals(asset.getStatus()) && asset.getWebStorageKey() == null)
                || ("PROCESSING".equals(asset.getStatus()) && asset.getOriginalStorageKey() == null)
                || ("DELETED".equals(asset.getStatus())
                    && (asset.getOriginalStorageKey() != null || asset.getWebStorageKey() != null));
    }
    private boolean staleUnreferenced(MediaAsset asset) {
        if (!("PROCESSING".equals(asset.getStatus()) || "FAILED".equals(asset.getStatus()))) return false;
        if (asset.getUpdatedAt() == null || asset.getUpdatedAt().isAfter(cleanup.now().minusHours(24))) return false;
        return !media.isReferenced(asset.getId());
    }
    private boolean activeJob(Long mediaId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_job WHERE media_id=? " +
                "AND status IN ('PENDING','RUNNING','RETRY')", Integer.class, mediaId);
        return count != null && count > 0;
    }
    private void persist(List<Issue> issues) {
        transactions.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            jdbc.update("UPDATE media_consistency_issue SET status='RESOLVED',resolved_at=?,last_detected_at=? WHERE status='OPEN'", now, now);
            for (Issue issue : issues) {
                String fingerprint = sha(issue.type + "|" + issue.mediaId + "|" + issue.storageKey);
                jdbc.update("INSERT INTO media_consistency_issue(fingerprint,issue_type,media_id,storage_key,status," +
                                "first_detected_at,last_detected_at,resolved_at) VALUES(?,?,?,?,'OPEN',?,?,NULL) " +
                                "ON DUPLICATE KEY UPDATE status='OPEN',last_detected_at=VALUES(last_detected_at),resolved_at=NULL",
                        fingerprint, issue.type, issue.mediaId, issue.storageKey, now, now);
            }
        });
    }
    private void add(Map<String, List<MediaAsset>> owners, String key, MediaAsset asset) {
        if (key != null) owners.computeIfAbsent(key, ignored -> new ArrayList<>()).add(asset);
    }
    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private record Issue(String type, Long mediaId, String storageKey) { }
    public record Report(int databaseAssets, int storedObjects, int openIssues, int cleanupJobsQueued) { }
}

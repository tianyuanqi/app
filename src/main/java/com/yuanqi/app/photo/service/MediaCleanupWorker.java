package com.yuanqi.app.photo.service;

import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 通过短租约领取任务；文件 I/O 不占用数据库行锁，崩溃后租约到期可恢复。 */
@Service
public class MediaCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanupWorker.class);
    private final JdbcTemplate jdbc;
    private final MediaAssetMapper media;
    private final StoragePort storage;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final int maxAttempts;

    public MediaCleanupWorker(JdbcTemplate jdbc, MediaAssetMapper media, StoragePort storage, Clock clock,
                              PlatformTransactionManager transactionManager,
                              @Value("${app.media.cleanup-max-attempts:6}") int maxAttempts) {
        this.jdbc = jdbc; this.media = media; this.storage = storage; this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager); this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.media.cleanup-delay-ms:60000}")
    public void run() { runDue(20); }

    /** 非 HTTP 的同步入口，供测试与运维 Runner 确定性驱动。 */
    public int runDue(int limit) {
        int handled = 0;
        for (int i = 0; i < limit; i++) {
            Job job = transactions.execute(status -> claim());
            if (job == null) break;
            execute(job); handled++;
        }
        return handled;
    }

    private Job claim() {
        LocalDateTime now = now();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,media_id,storage_key,attempt_count,deadline_at FROM media_cleanup_job " +
                        "WHERE ((status IN ('PENDING','RETRY') AND next_attempt_at<=?) " +
                        "OR (status='RUNNING' AND locked_until<=?)) ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
                now, now);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        jdbc.update("UPDATE media_cleanup_job SET status='RUNNING',attempt_count=attempt_count+1," +
                "locked_until=?,updated_at=? WHERE id=?", now.plusMinutes(5), now, id);
        Number mediaId = (Number) row.get("media_id");
        return new Job(id, mediaId == null ? null : mediaId.longValue(), (String) row.get("storage_key"),
                ((Number) row.get("attempt_count")).intValue() + 1,
                localDateTime(row.get("deadline_at")));
    }

    private void execute(Job job) {
        try {
            if (job.mediaId != null && !deletable(job)) return;
            if (job.mediaId == null) storage.delete(job.storageKey);
            else {
                MediaAsset asset = media.selectById(job.mediaId);
                if (asset != null) {
                    storage.delete(asset.getOriginalStorageKey());
                    storage.delete(asset.getWebStorageKey());
                }
            }
            transactions.executeWithoutResult(status -> complete(job));
        } catch (IOException | RuntimeException error) {
            log.warn("媒体清理任务暂时失败: jobId={}, category={}", job.id, error.getClass().getSimpleName());
            transactions.executeWithoutResult(status -> fail(job, error));
        }
    }

    private boolean deletable(Job job) {
        MediaAsset asset = media.selectById(job.mediaId);
        if (asset == null) return true;
        if (!"DELETE_PENDING".equals(asset.getStatus()) || media.isReferenced(job.mediaId)) {
            LocalDateTime now = now();
            transactions.executeWithoutResult(status -> jdbc.update(
                    "UPDATE media_cleanup_job SET status='CANCELLED',locked_until=NULL,completed_at=?,updated_at=? WHERE id=?",
                    now, now, job.id));
            return false;
        }
        return true;
    }

    private void complete(Job job) {
        LocalDateTime now = now();
        if (job.mediaId != null) {
            // MyBatis-Plus 默认忽略 null 字段，必须显式 SQL 才能彻底清空存储键。
            jdbc.update("UPDATE media_asset SET status='DELETED',original_storage_key=NULL,web_storage_key=NULL," +
                    "retryable=0,retry_until=NULL,updated_at=?,row_version=row_version+1 WHERE id=?", now, job.mediaId);
        }
        jdbc.update("UPDATE media_cleanup_job SET status='DELETED',locked_until=NULL,last_error_category=NULL," +
                "completed_at=?,updated_at=? WHERE id=?", now, now, job.id);
    }

    private void fail(Job job, Throwable error) {
        LocalDateTime now = now();
        boolean exhausted = job.attempts >= maxAttempts || !job.deadline.isAfter(now);
        LocalDateTime next = now.plusMinutes(Math.min(60, 1L << Math.min(6, job.attempts - 1)));
        jdbc.update("UPDATE media_cleanup_job SET status=?,locked_until=NULL,next_attempt_at=?," +
                        "last_error_category=?,completed_at=?,updated_at=? WHERE id=?",
                exhausted ? "EXHAUSTED" : "RETRY", next, category(error), exhausted ? now : null, now, job.id);
    }

    private String category(Throwable error) {
        String name = error.getClass().getSimpleName().toUpperCase();
        return name.length() <= 64 ? name : name.substring(0, 64);
    }
    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime local) return local;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("不支持的数据库时间类型");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private record Job(long id, Long mediaId, String storageKey, int attempts, LocalDateTime deadline) { }
}

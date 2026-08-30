package com.yuanqi.app.photo.service;

import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 在业务事务内完成逻辑撤权并登记独立、可恢复的物理清理任务。 */
@Service
public class MediaCleanupService {
    private final JdbcTemplate jdbc;
    private final MediaAssetMapper media;
    private final Clock clock;

    public MediaCleanupService(JdbcTemplate jdbc, MediaAssetMapper media, Clock clock) {
        this.jdbc = jdbc; this.media = media; this.clock = clock;
    }

    public void logicalDelete(MediaAsset asset, String reason) {
        if (asset == null || "DELETED".equals(asset.getStatus())) return;
        LocalDateTime now = now();
        asset.setStatus("DELETE_PENDING"); asset.setRetryable(false); asset.setRetryUntil(null);
        asset.setUpdatedAt(now); asset.setRowVersion(asset.getRowVersion() + 1); media.updateById(asset);
        enqueueMedia(asset.getId(), reason, now);
    }

    public void enqueueMedia(Long mediaId, String reason, LocalDateTime dueAt) {
        LocalDateTime now = now();
        jdbc.update("INSERT INTO media_cleanup_job(media_id,storage_key,reason,status,attempt_count,next_attempt_at," +
                        "deadline_at,created_at,updated_at) VALUES(?,NULL,?,'PENDING',0,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE reason=VALUES(reason)," +
                        "status=IF(status IN ('DELETED','CANCELLED','EXHAUSTED'),status,'PENDING')," +
                        "next_attempt_at=IF(status IN ('DELETED','CANCELLED','EXHAUSTED'),next_attempt_at,LEAST(next_attempt_at,VALUES(next_attempt_at)))," +
                        "locked_until=NULL,updated_at=VALUES(updated_at)",
                mediaId, reason, dueAt, dueAt.plusHours(24), now, now);
    }

    public void enqueueStorage(String storageKey, String reason, LocalDateTime dueAt) {
        if (storageKey == null) return;
        LocalDateTime now = now();
        jdbc.update("INSERT INTO media_cleanup_job(media_id,storage_key,reason,status,attempt_count,next_attempt_at," +
                        "deadline_at,created_at,updated_at) VALUES(NULL,?,?,'PENDING',0,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE reason=VALUES(reason)," +
                        "status=IF(status IN ('DELETED','CANCELLED','EXHAUSTED'),status,'PENDING')," +
                        "next_attempt_at=IF(status IN ('DELETED','CANCELLED','EXHAUSTED'),next_attempt_at,LEAST(next_attempt_at,VALUES(next_attempt_at)))," +
                        "locked_until=NULL,updated_at=VALUES(updated_at)",
                storageKey, reason, dueAt, dueAt.plusHours(24), now, now);
    }

    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}

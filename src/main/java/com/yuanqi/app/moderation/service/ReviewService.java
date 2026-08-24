package com.yuanqi.app.moderation.service;

import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.moderation.entity.ModerationEvent;
import com.yuanqi.app.moderation.mapper.ModerationEventMapper;
import com.yuanqi.app.moderation.vo.ModerationViews;
import com.yuanqi.app.photo.entity.PhotoRevision;
import com.yuanqi.app.photo.entity.PhotoWork;
import com.yuanqi.app.photo.mapper.PhotoRevisionMapper;
import com.yuanqi.app.photo.mapper.PhotoWorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.yuanqi.app.common.api.PageResult;
import com.yuanqi.app.common.http.StrongEtag;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ReviewService {
    private final PhotoWorkMapper workMapper;
    private final PhotoRevisionMapper revisionMapper;
    private final ModerationEventMapper eventMapper;
    private final AccountMapper accountMapper;
    private final PublicIdGenerator ids;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public ReviewService(PhotoWorkMapper workMapper, PhotoRevisionMapper revisionMapper,
                         ModerationEventMapper eventMapper, AccountMapper accountMapper,
                         PublicIdGenerator ids, Clock clock, JdbcTemplate jdbc) {
        this.workMapper = workMapper; this.revisionMapper = revisionMapper; this.eventMapper = eventMapper;
        this.accountMapper = accountMapper; this.ids = ids; this.clock = clock;
        this.jdbc=jdbc;
    }

    @Transactional(readOnly=true) public PageResult<ModerationViews.TargetSummary> queue(int page,int size){if(page<1)throw new BusinessException(ErrorCode.INVALID_PAGE);if(size<1||size>100)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);long total=jdbc.queryForObject("SELECT COUNT(*) FROM photo_revision WHERE state='PENDING'",Long.class);var items=jdbc.query("SELECT w.work_id,r.revision_id,a.uid,r.title,r.submitted_at,w.row_version FROM photo_revision r JOIN photo_work w ON w.working_revision_id=r.id JOIN user_account a ON a.id=w.author_account_id WHERE r.state='PENDING' ORDER BY r.submitted_at,r.revision_id LIMIT ? OFFSET ?",(rs,n)->new ModerationViews.TargetSummary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime().atOffset(ZoneOffset.UTC),"\"work-"+rs.getLong(6)+"\""),size,(page-1)*size);return PageResult.of(items,page,size,total);}
    @Transactional(readOnly=true) public ModerationViews.AdminPhotoSummary summary(String workId){PhotoWork w=workMapper.findByPublicId(workId);if(w==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);PhotoRevision working=w.getWorkingRevisionId()==null?null:revisionMapper.selectById(w.getWorkingRevisionId());boolean pending=working!=null&&"PENDING".equals(working.getState());if("DRAFT".equals(w.getPublicationState())&&!pending)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);PhotoRevision published=w.getPublicRevisionId()==null?null:revisionMapper.selectById(w.getPublicRevisionId());java.util.List<String> actions=new java.util.ArrayList<>();if(pending){actions.add("APPROVE");actions.add("REJECT");}if("PUBLISHED".equals(w.getPublicationState()))actions.add("OFFLINE");actions.add("DELETE");return new ModerationViews.AdminPhotoSummary(w.getWorkId(),w.getPublicationState(),working==null?null:working.getRevisionId(),working==null?null:working.getState(),published==null?null:published.getRevisionId(),java.util.List.copyOf(actions),tag(w));}
    @Transactional(readOnly=true) public ModerationViews.Target target(Long reviewer,String workId,String revisionId){var rows=jdbc.query("SELECT w.id,w.work_id,w.author_account_id,w.row_version,r.id,r.revision_id,r.title,r.description,r.location,r.submitted_at,a.uid FROM photo_work w JOIN photo_revision r ON r.id=w.working_revision_id JOIN user_account a ON a.id=w.author_account_id WHERE w.work_id=? AND r.revision_id=? AND r.state='PENDING'",(rs,n)->{long rid=rs.getLong(5);var media=jdbc.queryForList("SELECT m.media_id FROM revision_media rm JOIN media_asset m ON m.id=rm.media_id WHERE rm.revision_id=? ORDER BY rm.position",String.class,rid);return new ModerationViews.Target(rs.getString(2),rs.getString(6),rs.getString(11),rs.getString(7),rs.getString(8),rs.getString(9),media,rs.getTimestamp(10).toLocalDateTime().atOffset(ZoneOffset.UTC),rs.getLong(3)==reviewer,"\"work-"+rs.getLong(4)+"\"");},workId,revisionId);if(rows.isEmpty())throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return rows.get(0);}
    @Transactional(readOnly=true) public PageResult<ModerationViews.Event> history(String workId,int page,int size){if(page<1)throw new BusinessException(ErrorCode.INVALID_PAGE);if(size<1||size>100)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);PhotoWork w=workMapper.findByPublicId(workId);if(w==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);long total=jdbc.queryForObject("SELECT COUNT(*) FROM moderation_event WHERE work_id=?",Long.class,w.getId());var rows=jdbc.query("SELECT e.event_id,r.revision_id,e.action,e.previous_state,e.resulting_state,sa.uid,ra.uid,e.reason,e.self_review,e.occurred_at FROM moderation_event e JOIN photo_revision r ON r.id=e.revision_id JOIN user_account sa ON sa.id=e.submitter_account_id LEFT JOIN user_account ra ON ra.id=e.reviewer_account_id WHERE e.work_id=? ORDER BY e.occurred_at DESC,e.event_id DESC LIMIT ? OFFSET ?",(rs,n)->new ModerationViews.Event(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getBoolean(9),rs.getTimestamp(10).toLocalDateTime().atOffset(ZoneOffset.UTC)),w.getId(),size,(page-1)*size);return PageResult.of(rows,page,size,total);}

    @Transactional(noRollbackFor = AccountUnavailableAfterReviewLock.class)
    public ModerationViews.Mutation approve(Long reviewerId, String workId, String revisionId, String ifMatch) {
        PhotoWork work = lock(workId); match(ifMatch, work);
        PhotoRevision revision = pendingTarget(work, revisionId);
        Account author = accountMapper.selectById(work.getAuthorAccountId());
        if (author == null || "DISABLED".equals(author.getGovernanceStatus())) {
            revision.setState("DRAFT"); revision.setUpdatedAt(now()); revision.setRowVersion(revision.getRowVersion() + 1);
            revisionMapper.updateById(revision); bump(work); event(work, revision, reviewerId, "AUTHOR_DISABLED",
                    "PENDING", "DRAFT", "作者账号已禁用");
            throw new AccountUnavailableAfterReviewLock();
        }
        PhotoRevision old = work.getPublicRevisionId() == null ? null : revisionMapper.selectById(work.getPublicRevisionId());
        if (old != null && !old.getId().equals(revision.getId())) {
            old.setState("SUPERSEDED"); old.setUpdatedAt(now()); old.setRowVersion(old.getRowVersion() + 1);
            revisionMapper.updateById(old);
        }
        revision.setState("PUBLISHED"); revision.setUpdatedAt(now()); revision.setRowVersion(revision.getRowVersion() + 1);
        revisionMapper.updateById(revision);
        work.setPublicRevisionId(revision.getId()); work.setWorkingRevisionId(null);
        work.setPublicationState("PUBLISHED"); work.setPublishedAt(now()); bump(work);
        LocalDateTime occurred = event(work, revision, reviewerId, "APPROVE", "PENDING", "PUBLISHED", null);
        return result(work, revision, "APPROVED", occurred);
    }

    @Transactional
    public ModerationViews.Mutation reject(Long reviewerId, String workId, String revisionId,
                                           String ifMatch, String rawReason) {
        PhotoWork work = lock(workId); match(ifMatch, work);
        PhotoRevision revision = pendingTarget(work, revisionId); String reason = AuthPolicy.validateReason(rawReason);
        revision.setState("REJECTED"); revision.setUpdatedAt(now()); revision.setRowVersion(revision.getRowVersion() + 1);
        revisionMapper.updateById(revision); bump(work);
        LocalDateTime occurred = event(work, revision, reviewerId, "REJECT", "PENDING", "REJECTED", reason);
        return result(work, revision, "REJECTED", occurred);
    }

    @Transactional
    public ModerationViews.Mutation offline(Long reviewerId, String workId, String ifMatch, String rawReason) {
        PhotoWork work = lock(workId); match(ifMatch, work); String reason = AuthPolicy.validateReason(rawReason);
        if (!"PUBLISHED".equals(work.getPublicationState()) || work.getPublicRevisionId() == null)
            throw new BusinessException(ErrorCode.STATE_CONFLICT);
        PhotoRevision target = revisionMapper.selectById(work.getPublicRevisionId());
        PhotoRevision working = work.getWorkingRevisionId() == null ? null : revisionMapper.selectById(work.getWorkingRevisionId());
        if (working != null && "PENDING".equals(working.getState())) {
            working.setState("DRAFT"); working.setOrigin("OFFLINE_REWORK"); working.setUpdatedAt(now());
            working.setRowVersion(working.getRowVersion() + 1); revisionMapper.updateById(working);
        }
        work.setPublicationState("OFFLINE"); bump(work);
        LocalDateTime occurred = event(work, target, reviewerId, "OFFLINE", "PUBLISHED", "OFFLINE", reason);
        return result(work, target, "OFFLINED", occurred);
    }

    private PhotoRevision pendingTarget(PhotoWork work, String revisionId) {
        PhotoRevision revision = revisionMapper.findByPublicId(revisionId);
        if (revision == null || !revision.getId().equals(work.getWorkingRevisionId()) || !"PENDING".equals(revision.getState()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return revision;
    }

    private LocalDateTime event(PhotoWork work, PhotoRevision revision, Long reviewerId, String action,
                                String before, String after, String reason) {
        LocalDateTime now = now(); ModerationEvent event = new ModerationEvent(); event.setEventId(ids.next());
        event.setWorkId(work.getId()); event.setRevisionId(revision.getId()); event.setAction(action);
        event.setPreviousState(before); event.setResultingState(after); event.setSubmitterAccountId(work.getAuthorAccountId());
        event.setReviewerAccountId(reviewerId); event.setReason(reason);
        event.setSelfReview(work.getAuthorAccountId().equals(reviewerId)); event.setOccurredAt(now); eventMapper.insert(event);
        return now;
    }

    private ModerationViews.Mutation result(PhotoWork work, PhotoRevision revision, String action, LocalDateTime at) {
        PhotoRevision working = work.getWorkingRevisionId() == null ? null : revisionMapper.selectById(work.getWorkingRevisionId());
        return new ModerationViews.Mutation(work.getWorkId(), revision.getRevisionId(), action,
                work.getPublicationState(), working == null ? null : working.getState(), at.atOffset(ZoneOffset.UTC), tag(work));
    }
    private PhotoWork lock(String id) { PhotoWork w = workMapper.findByPublicIdForUpdate(id); if (w == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return w; }
    private void match(String value, PhotoWork work) { StrongEtag.requireMatch(value, tag(work)); }
    private String tag(PhotoWork w) { return "\"work-" + w.getRowVersion() + "\""; }
    private void bump(PhotoWork w) { w.setRowVersion(w.getRowVersion() + 1); w.setUpdatedAt(now()); workMapper.updateById(w); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private static final class AccountUnavailableAfterReviewLock extends BusinessException {
        private AccountUnavailableAfterReviewLock() { super(ErrorCode.ACCOUNT_UNAVAILABLE); }
    }
}

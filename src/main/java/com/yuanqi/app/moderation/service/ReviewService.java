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

    public ReviewService(PhotoWorkMapper workMapper, PhotoRevisionMapper revisionMapper,
                         ModerationEventMapper eventMapper, AccountMapper accountMapper,
                         PublicIdGenerator ids, Clock clock) {
        this.workMapper = workMapper; this.revisionMapper = revisionMapper; this.eventMapper = eventMapper;
        this.accountMapper = accountMapper; this.ids = ids; this.clock = clock;
    }

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
    private void match(String value, PhotoWork work) { if (value == null) throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED); if (!tag(work).equals(value)) throw new BusinessException(ErrorCode.PRECONDITION_FAILED); }
    private String tag(PhotoWork w) { return "\"work-" + w.getRowVersion() + "\""; }
    private void bump(PhotoWork w) { w.setRowVersion(w.getRowVersion() + 1); w.setUpdatedAt(now()); workMapper.updateById(w); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private static final class AccountUnavailableAfterReviewLock extends BusinessException {
        private AccountUnavailableAfterReviewLock() { super(ErrorCode.ACCOUNT_UNAVAILABLE); }
    }
}

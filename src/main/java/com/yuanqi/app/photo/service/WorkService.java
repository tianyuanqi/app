package com.yuanqi.app.photo.service;

import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.http.StrongEtag;
import com.yuanqi.app.common.text.UnicodeText;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.entity.PhotoRevision;
import com.yuanqi.app.photo.entity.PhotoWork;
import com.yuanqi.app.photo.entity.RevisionMedia;
import com.yuanqi.app.photo.entity.PhotoCategory;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import com.yuanqi.app.photo.mapper.PhotoRevisionMapper;
import com.yuanqi.app.photo.mapper.PhotoWorkMapper;
import com.yuanqi.app.photo.mapper.RevisionMediaMapper;
import com.yuanqi.app.photo.mapper.PhotoCategoryMapper;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import com.yuanqi.app.photo.mapper.RevisionTagMapper;
import com.yuanqi.app.photo.vo.WorkViews;
import com.yuanqi.app.photo.vo.MediaViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.yuanqi.app.common.api.PageResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkService {
    private final PhotoWorkMapper workMapper;
    private final PhotoRevisionMapper revisionMapper;
    private final RevisionMediaMapper revisionMediaMapper;
    private final MediaAssetMapper mediaMapper;
    private final PublicIdGenerator ids;
    private final Clock clock;
    private final PhotoCategoryMapper categoryMapper;
    private final PhotoTagMapper tagMapper;
    private final RevisionTagMapper revisionTagMapper;
    private final JdbcTemplate jdbc;

    public WorkService(PhotoWorkMapper workMapper, PhotoRevisionMapper revisionMapper,
                       RevisionMediaMapper revisionMediaMapper, MediaAssetMapper mediaMapper,
                       PublicIdGenerator ids, Clock clock, PhotoCategoryMapper categoryMapper,
                       PhotoTagMapper tagMapper, RevisionTagMapper revisionTagMapper,JdbcTemplate jdbc) {
        this.workMapper = workMapper;
        this.revisionMapper = revisionMapper;
        this.revisionMediaMapper = revisionMediaMapper;
        this.mediaMapper = mediaMapper;
        this.ids = ids;
        this.clock = clock;
        this.categoryMapper = categoryMapper; this.tagMapper = tagMapper; this.revisionTagMapper = revisionTagMapper;
        this.jdbc=jdbc;
    }

    @Transactional
    public WorkViews.AuthorWork create(Long accountId, WorkRequests.Draft request) {
        LocalDateTime now = now();
        PhotoWork work = new PhotoWork();
        work.setWorkId(ids.next());
        work.setAuthorAccountId(accountId);
        work.setPublicationState("NEVER_PUBLISHED");
        work.setRowVersion(0L);
        work.setCreatedAt(now);
        work.setUpdatedAt(now);
        workMapper.insert(work);
        PhotoRevision revision = newRevision(work, "NEW", 1, request, now);
        work.setWorkingRevisionId(revision.getId());
        workMapper.updateById(work);
        bindMedia(accountId, revision.getId(), request.mediaIds());
        bindTags(revision.getId(), request.tags());
        return view(work, null, revision);
    }

    @Transactional(readOnly = true)
    public WorkViews.AuthorWork authorView(Long accountId, String workId) {
        PhotoWork work = owned(workMapper.findByPublicId(workId), accountId);
        return view(work, revision(work.getPublicRevisionId()), revision(work.getWorkingRevisionId()));
    }
    @Transactional(readOnly=true) public WorkViews.Revision draft(Long accountId,String workId){PhotoWork w=owned(workMapper.findByPublicId(workId),accountId);PhotoRevision r=revision(w.getWorkingRevisionId());if(r==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return revisionView(r);}
    @Transactional(readOnly=true) public PageResult<WorkViews.Summary> mine(Long accountId,int page,int size){if(page<1)throw new BusinessException(ErrorCode.INVALID_PAGE);if(size<1||size>100)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);long total=jdbc.queryForObject("SELECT COUNT(*) FROM photo_work WHERE author_account_id=?",Long.class,accountId);var works=jdbc.query("SELECT * FROM photo_work WHERE author_account_id=? ORDER BY updated_at DESC,work_id DESC LIMIT ? OFFSET ?",(rs,n)->{PhotoWork w=new PhotoWork();w.setId(rs.getLong("id"));w.setWorkId(rs.getString("work_id"));w.setPublicationState(rs.getString("publication_state"));w.setWorkingRevisionId((Long)rs.getObject("working_revision_id"));w.setPublishedAt(rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toLocalDateTime());w.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());w.setRowVersion(rs.getLong("row_version"));PhotoRevision r=revision(w.getWorkingRevisionId());WorkViews.Capabilities c=new WorkViews.Capabilities(r==null,r!=null&&"DRAFT".equals(r.getState()),r!=null&&java.util.Set.of("DRAFT","REJECTED").contains(r.getState()),r!=null&&"PENDING".equals(r.getState()),true);return new WorkViews.Summary(w.getWorkId(),w.getPublicationState(),r==null?null:r.getRevisionId(),r==null?null:r.getState(),utc(w.getPublishedAt()),utc(w.getUpdatedAt()),jdbc.queryForObject("SELECT COUNT(*) FROM photo_like WHERE work_id=?",Long.class,w.getId()),jdbc.queryForObject("SELECT COUNT(*) FROM photo_comment WHERE work_id=? AND display_state='ACTIVE'",Long.class,w.getId()),c,tag(w.getRowVersion()));},accountId,size,(page-1)*size);return PageResult.of(works,page,size,total);}

    @Transactional
    public WorkViews.AuthorWork updateDraft(Long accountId, String workId, String ifMatch,
                                            WorkRequests.Draft request) {
        PhotoWork work = lockOwned(workId, accountId);
        match(ifMatch, work.getRowVersion());
        PhotoRevision revision = revision(work.getWorkingRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getState())) conflict();
        apply(revision, request);
        revision.setUpdatedAt(now());
        revision.setRowVersion(revision.getRowVersion() + 1);
        revisionMapper.updateById(revision);
        bindMedia(accountId, revision.getId(), request.mediaIds());
        bindTags(revision.getId(), request.tags());
        bump(work);
        return view(work, revision(work.getPublicRevisionId()), revision);
    }

    @Transactional
    public WorkViews.AuthorWork submit(Long accountId, String workId, String ifMatch) {
        PhotoWork work = lockOwned(workId, accountId);
        match(ifMatch, work.getRowVersion());
        PhotoRevision revision = revision(work.getWorkingRevisionId());
        if (revision == null || !("DRAFT".equals(revision.getState()) || "REJECTED".equals(revision.getState()))) conflict();
        validateReadyMedia(accountId, revisionMediaMapper.listByRevision(revision.getId()));
        revision.setState("PENDING");
        revision.setSubmittedAt(now());
        revision.setUpdatedAt(now());
        revision.setRowVersion(revision.getRowVersion() + 1);
        revisionMapper.updateById(revision);
        bump(work);
        return view(work, revision(work.getPublicRevisionId()), revision);
    }

    @Transactional
    public WorkViews.AuthorWork withdraw(Long accountId, String workId, String ifMatch) {
        PhotoWork work = lockOwned(workId, accountId);
        match(ifMatch, work.getRowVersion());
        PhotoRevision revision = revision(work.getWorkingRevisionId());
        if (revision == null || !"PENDING".equals(revision.getState())) conflict();
        revision.setState("DRAFT");
        revision.setUpdatedAt(now());
        revision.setRowVersion(revision.getRowVersion() + 1);
        revisionMapper.updateById(revision);
        bump(work);
        return view(work, revision(work.getPublicRevisionId()), revision);
    }

    @Transactional
    public WorkViews.AuthorWork createDraft(Long accountId, String workId, String ifMatch) {
        PhotoWork work = lockOwned(workId, accountId);
        match(ifMatch, work.getRowVersion());
        PhotoRevision current = revision(work.getWorkingRevisionId());
        if (current != null && "DRAFT".equals(current.getState())) return view(work, revision(work.getPublicRevisionId()), current);
        if (current != null && "PENDING".equals(current.getState())) conflict();
        if (current != null && "REJECTED".equals(current.getState())) {
            current.setState("DRAFT");
            current.setOrigin("REJECTED_REWORK");
            current.setRowVersion(current.getRowVersion() + 1);
            current.setUpdatedAt(now());
            revisionMapper.updateById(current);
            bump(work);
            return view(work, revision(work.getPublicRevisionId()), current);
        }
        PhotoRevision source = revision(work.getPublicRevisionId());
        if (source == null) conflict();
        String origin = "OFFLINE".equals(work.getPublicationState()) ? "OFFLINE_REWORK" : "EDIT_PUBLISHED";
        PhotoRevision copy = copyRevision(work, source, origin);
        work.setWorkingRevisionId(copy.getId());
        copyMedia(source.getId(), copy.getId());
        bump(work);
        return view(work, source, copy);
    }

    private PhotoRevision newRevision(PhotoWork work, String origin, int number,
                                      WorkRequests.Draft request, LocalDateTime now) {
        PhotoRevision revision = new PhotoRevision();
        revision.setRevisionId(ids.next());
        revision.setWorkId(work.getId());
        revision.setRevisionNumber(number);
        revision.setState("DRAFT");
        revision.setOrigin(origin);
        apply(revision, request);
        revision.setCreatedAt(now);
        revision.setUpdatedAt(now);
        revision.setRowVersion(0L);
        revisionMapper.insert(revision);
        return revision;
    }

    private PhotoRevision copyRevision(PhotoWork work, PhotoRevision source, String origin) {
        PhotoRevision copy = new PhotoRevision();
        copy.setRevisionId(ids.next()); copy.setWorkId(work.getId());
        copy.setRevisionNumber(revisionMapper.maxRevisionNumber(work.getId()) + 1);
        copy.setState("DRAFT"); copy.setOrigin(origin); copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription()); copy.setLocation(source.getLocation());
        copy.setCategoryId(source.getCategoryId()); copy.setCreatedAt(now()); copy.setUpdatedAt(now());
        copy.setRowVersion(0L); revisionMapper.insert(copy); return copy;
    }

    private void copyMedia(Long sourceId, Long targetId) {
        for (RevisionMedia item : revisionMediaMapper.listByRevision(sourceId)) {
            item.setRevisionId(targetId);
            revisionMediaMapper.insert(item);
        }
    }

    private void bindMedia(Long accountId, Long revisionId, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 9 || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品必须包含 1～9 张不重复照片");
        }
        revisionMediaMapper.deleteByRevision(revisionId);
        long total = 0;
        int position = 1;
        for (String publicId : ids) {
            MediaAsset media = mediaMapper.findByPublicId(publicId);
            if (media == null || !accountId.equals(media.getOwnerAccountId()) || !"PHOTO".equals(media.getPurpose())
                    || !"READY".equals(media.getStatus())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            total += media.getByteSize() == null ? 0 : media.getByteSize();
            RevisionMedia relation = new RevisionMedia();
            relation.setRevisionId(revisionId); relation.setMediaId(media.getId()); relation.setPosition(position++);
            relation.setParameterSource("NONE"); revisionMediaMapper.insert(relation);
        }
        if (total > 200L * 1024 * 1024) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
    }

    private void validateReadyMedia(Long accountId, List<RevisionMedia> relations) {
        if (relations.isEmpty() || relations.size() > 9) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        for (RevisionMedia relation : relations) {
            MediaAsset media = mediaMapper.selectById(relation.getMediaId());
            if (media == null || !accountId.equals(media.getOwnerAccountId()) || !"READY".equals(media.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT);
            }
        }
    }

    private void apply(PhotoRevision target, WorkRequests.Draft request) {
        target.setTitle(text(request.title(), 100, false));
        target.setDescription(text(request.description(), 5000, true));
        target.setLocation(text(request.location(), 100, false));
        if (request.categoryId() == null) target.setCategoryId(null);
        else { PhotoCategory category=categoryMapper.findByPublicId(request.categoryId());
            if(category==null||!Boolean.TRUE.equals(category.getActive()))throw new BusinessException(ErrorCode.VALIDATION_FAILED,"分类不可用于新作品");
            target.setCategoryId(category.getId()); }
    }

    private void bindTags(Long revisionId, List<String> values) {
        revisionTagMapper.deleteByRevision(revisionId);
        if (values == null || values.isEmpty()) return;
        if (values.size() > 5) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "每篇最多五个标签");
        Set<String> keys = new HashSet<>(); int position=1;
        for(String raw:values){String display=UnicodeText.nfc(UnicodeText.trimUnicode(raw));
            if(display==null||UnicodeText.graphemeCount(display)<1||UnicodeText.graphemeCount(display)>20
                    ||UnicodeText.containsForbiddenControl(display,false))throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            String key=UnicodeText.comparisonKey(display);if(!keys.add(key))throw new BusinessException(ErrorCode.VALIDATION_FAILED,"同一作品标签不得重复");
            PhotoTag tag=tagMapper.findByNormalizedName(key);if(tag==null){tag=new PhotoTag();tag.setTagId(ids.next());tag.setDisplayName(display);tag.setNormalizedName(key);tag.setCreatedAt(now());
                try{tagMapper.insert(tag);}catch(org.springframework.dao.DuplicateKeyException e){tag=tagMapper.findByNormalizedName(key);}}
            revisionTagMapper.insert(revisionId,tag.getId(),position++);
        }
    }

    private String text(String raw, int max, boolean lines) {
        if (raw == null) return null;
        String value = UnicodeText.nfc(UnicodeText.trimUnicode(raw.replace("\r\n", "\n")));
        if (value.isEmpty()) return null;
        if (UnicodeText.graphemeCount(value) > max || UnicodeText.containsForbiddenControl(value, lines)
                || (!lines && value.contains("\n"))) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return value;
    }

    private WorkViews.AuthorWork view(PhotoWork work, PhotoRevision published, PhotoRevision working) {
        WorkViews.Capabilities caps = new WorkViews.Capabilities(working == null,
                working != null && "DRAFT".equals(working.getState()),
                working != null && ("DRAFT".equals(working.getState()) || "REJECTED".equals(working.getState())),
                working != null && "PENDING".equals(working.getState()), true);
        WorkViews.Summary summary = new WorkViews.Summary(work.getWorkId(), work.getPublicationState(),
                working == null ? null : working.getRevisionId(), working == null ? null : working.getState(),
                utc(work.getPublishedAt()), utc(work.getUpdatedAt()), 0, 0, caps, tag(work.getRowVersion()));
        return new WorkViews.AuthorWork(summary, publicRevisionView(published, work.getPublishedAt()), revisionView(working));
    }

    public WorkViews.Revision revisionView(PhotoRevision revision) {
        if (revision == null) return null;
        return new WorkViews.Revision(revision.getRevisionId(), revision.getRevisionNumber(), revision.getState(),
                revision.getOrigin(), revision.getTitle(), revision.getDescription(), revision.getLocation(),
                category(revision), tags(revision), media(revision),
                utc(revision.getCreatedAt()), utc(revision.getUpdatedAt()), utc(revision.getSubmittedAt()),
                "\"revision-" + revision.getRowVersion() + "\"");
    }

    public WorkViews.PublicRevision publicRevisionView(PhotoRevision revision, LocalDateTime publishedAt) {
        if (revision == null) return null;
        return new WorkViews.PublicRevision(revision.getRevisionId(), revision.getRevisionNumber(), revision.getTitle(),
                revision.getDescription(), revision.getLocation(), category(revision), tags(revision), media(revision),
                utc(publishedAt));
    }

    private WorkViews.Category category(PhotoRevision revision) {
        if (revision.getCategoryId() == null) return null;
        PhotoCategory value = categoryMapper.selectById(revision.getCategoryId());
        return value == null ? null : new WorkViews.Category(value.getPublicId(), value.getName(),
                Boolean.TRUE.equals(value.getActive()), true);
    }

    private List<WorkViews.Tag> tags(PhotoRevision revision) {
        return jdbc.query("SELECT t.tag_id,t.display_name FROM revision_tag rt JOIN photo_tag t ON t.id=rt.tag_id " +
                        "WHERE rt.revision_id=? ORDER BY rt.position",
                (rs, row) -> new WorkViews.Tag(rs.getString(1), rs.getString(2)), revision.getId());
    }

    private List<WorkViews.RevisionMedia> media(PhotoRevision revision) {
        return revisionMediaMapper.listByRevision(revision.getId()).stream().map(relation -> {
            MediaAsset asset = mediaMapper.selectById(relation.getMediaId());
            if (asset == null || !"READY".equals(asset.getStatus()) || asset.getWebStorageKey() == null)
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "Revision 媒体未完成处理");
            MediaViews.WebMedia web = new MediaViews.WebMedia(asset.getMediaId(),
                    mediaMapper.isPublicWeb(asset.getId()) ? "PUBLIC_URL" : "BEARER_FETCH",
                    "/api/v1/media/" + asset.getMediaId() + "/web", "image/jpeg",
                    asset.getWidth(), asset.getHeight(), "\"media-" + asset.getRowVersion() + "\"");
            WorkViews.PhotoParameters parameters = new WorkViews.PhotoParameters(
                    relation.getCaptureTime() == null ? null
                            : relation.getCaptureTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),
                    relation.getCameraBody(), relation.getLens(), relation.getFocalLength(), relation.getAperture(),
                    relation.getShutterSpeed(), relation.getIsoValue());
            return new WorkViews.RevisionMedia(asset.getMediaId(), relation.getPosition(),
                    relation.getPosition() == 1, web, parameters);
        }).toList();
    }

    private PhotoWork lockOwned(String id, Long accountId) { return owned(workMapper.findByPublicIdForUpdate(id), accountId); }
    private PhotoWork owned(PhotoWork work, Long accountId) {
        if (work == null || !accountId.equals(work.getAuthorAccountId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return work;
    }
    private PhotoRevision revision(Long id) { return id == null ? null : revisionMapper.selectById(id); }
    private void match(String value, long version) {
        StrongEtag.requireMatch(value, tag(version));
    }
    private String tag(long version) { return "\"work-" + version + "\""; }
    private void bump(PhotoWork work) { work.setRowVersion(work.getRowVersion() + 1); work.setUpdatedAt(now()); workMapper.updateById(work); }
    private void conflict() { throw new BusinessException(ErrorCode.STATE_CONFLICT); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private OffsetDateTime utc(LocalDateTime value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
}

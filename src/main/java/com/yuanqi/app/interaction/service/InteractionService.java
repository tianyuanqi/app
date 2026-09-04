package com.yuanqi.app.interaction.service;

import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.support.CryptoSupport;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.text.UnicodeText;
import com.yuanqi.app.interaction.entity.PhotoComment;
import com.yuanqi.app.interaction.entity.CommentModerationEvent;
import com.yuanqi.app.interaction.mapper.PhotoCommentMapper;
import com.yuanqi.app.interaction.mapper.PhotoLikeMapper;
import com.yuanqi.app.interaction.mapper.CommentModerationEventMapper;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.interaction.vo.InteractionViews;
import com.yuanqi.app.photo.entity.PhotoWork;
import com.yuanqi.app.photo.mapper.PhotoWorkMapper;
import com.yuanqi.app.user.entity.UserProfile;
import com.yuanqi.app.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Service
public class InteractionService {
    private final PhotoWorkMapper workMapper; private final PhotoLikeMapper likeMapper;
    private final PhotoCommentMapper commentMapper; private final AccountMapper accountMapper;
    private final UserProfileMapper profileMapper; private final PublicIdGenerator ids;
    private final CryptoSupport crypto; private final Clock clock;
    private final CommentModerationEventMapper moderationEvents;

    public InteractionService(PhotoWorkMapper workMapper, PhotoLikeMapper likeMapper,
                              PhotoCommentMapper commentMapper, AccountMapper accountMapper,
                              UserProfileMapper profileMapper, PublicIdGenerator ids,
                              CryptoSupport crypto, Clock clock, CommentModerationEventMapper moderationEvents) {
        this.workMapper=workMapper; this.likeMapper=likeMapper; this.commentMapper=commentMapper;
        this.accountMapper=accountMapper; this.profileMapper=profileMapper; this.ids=ids;
        this.crypto=crypto; this.clock=clock;
        this.moderationEvents=moderationEvents;
    }

    @Transactional
    public InteractionViews.LikeMutation like(Long accountId, String workId, boolean desired) {
        PhotoWork work = publishedLocked(workId);
        if (desired) likeMapper.like(work.getId(), accountId, now()); else likeMapper.unlike(work.getId(), accountId);
        return new InteractionViews.LikeMutation(desired, likeMapper.count(work.getId()),
                likeMapper.received(work.getAuthorAccountId()));
    }

    @Transactional
    public InteractionViews.CommentCreate createComment(Long accountId, String workId, String raw) {
        PhotoWork work = publishedLocked(workId); PhotoComment comment = insert(work, accountId, null, raw);
        return new InteractionViews.CommentCreate(thread(comment, accountId), commentMapper.activeCount(work.getId()));
    }

    @Transactional
    public InteractionViews.ReplyCreate createReply(Long accountId, String workId, String rootId, String raw) {
        PhotoWork work = publishedLocked(workId); PhotoComment root = commentMapper.findForUpdate(rootId);
        if (root == null || !root.getWorkId().equals(work.getId()) || root.getRootCommentId()!=null)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"ACTIVE".equals(root.getDisplayState())) throw new BusinessException(ErrorCode.STATE_CONFLICT);
        PhotoComment reply = insert(work, accountId, root.getId(), raw);
        return new InteractionViews.ReplyCreate(reply(reply, accountId), commentMapper.activeCount(work.getId()),
                commentMapper.activeReplyCount(root.getId()));
    }

    @Transactional(readOnly = true)
    public InteractionViews.CommentPage comments(Long viewer, String workId, int page) {
        if (page < 1) throw new BusinessException(ErrorCode.INVALID_PAGE);
        PhotoWork work = published(workId); long total = commentMapper.rootCount(work.getId());
        List<InteractionViews.ThreadItem> items = commentMapper.roots(work.getId(), (long)(page-1)*20, 20)
                .stream().map(c -> thread(c, viewer)).toList();
        int pages=total==0?0:(int)((total+19)/20);
        return new InteractionViews.CommentPage(items,page,20,total,pages,page>1,page<pages,
                commentMapper.activeCount(work.getId()));
    }

    @Transactional(readOnly = true)
    public InteractionViews.CursorPage<InteractionViews.Reply> replies(Long viewer, String workId,
                                                                        String rootId, String cursor, int limit) {
        if (limit<1 || limit>100) throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        PhotoWork work=published(workId); PhotoComment root=commentMapper.findForUpdate(rootId);
        if(root==null||!root.getWorkId().equals(work.getId())||root.getRootCommentId()!=null)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Cursor decoded=decode(cursor,rootId);
        List<PhotoComment> rows=commentMapper.repliesAfter(root.getId(),decoded.at(),decoded.commentId(),limit+1);
        boolean more=rows.size()>limit; List<PhotoComment> selected=more?rows.subList(0,limit):rows;
        String next=more?encode(rootId,selected.get(selected.size()-1)):null;
        return new InteractionViews.CursorPage<>(selected.stream().map(r->reply(r,viewer)).toList(),next,more);
    }

    @Transactional
    public InteractionViews.CommentMutation deleteOwn(Long accountId,String workId,String commentId) {
        return delete(accountId,workId,commentId,false);
    }

    @Transactional
    public InteractionViews.CommentMutation deleteAdmin(Long adminId,String commentId,String reason) {
        PhotoComment target=commentMapper.findForUpdate(commentId);
        if(target==null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        PhotoWork work=workMapper.selectById(target.getWorkId());
        if(work==null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        String validated=AuthPolicy.validateReason(reason);String publicId=target.getCommentId();
        InteractionViews.CommentMutation result=deleteTarget(adminId,work,target,true);
        CommentModerationEvent event=new CommentModerationEvent();event.setEventId(ids.next());event.setCommentId(publicId);
        event.setActorAccountId(adminId);event.setReason(validated);event.setOccurredAt(now());moderationEvents.insert(event);
        return result;
    }

    private InteractionViews.CommentMutation delete(Long actor,String workId,String commentId,boolean admin) {
        PhotoWork work=publishedLocked(workId); PhotoComment target=commentMapper.findForUpdate(commentId);
        if(target==null||!target.getWorkId().equals(work.getId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return deleteTarget(actor,work,target,admin);
    }

    private InteractionViews.CommentMutation deleteTarget(Long actor,PhotoWork work,PhotoComment target,boolean admin) {
        if(!admin&&!actor.equals(target.getAuthorAccountId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        String rootPublic=target.getCommentId(),outcome; long replies;
        if(target.getRootCommentId()==null){
            replies=commentMapper.activeReplyCount(target.getId());
            if(replies>0){ target.setDisplayState("DELETED_PLACEHOLDER");target.setContent(null);target.setDeletedAt(now());
                target.setRowVersion(target.getRowVersion()+1);commentMapper.updateById(target);outcome="ROOT_BECAME_PLACEHOLDER"; }
            else {commentMapper.hardDelete(target.getId());outcome="ROOT_REMOVED";}
        }else{
            PhotoComment root=commentMapper.selectById(target.getRootCommentId()); rootPublic=root.getCommentId();
            commentMapper.hardDelete(target.getId()); replies=commentMapper.activeReplyCount(root.getId());
            if(replies==0&&"DELETED_PLACEHOLDER".equals(root.getDisplayState())){
                commentMapper.hardDelete(root.getId());outcome="REPLY_AND_ROOT_REMOVED";
            }else outcome="REPLY_REMOVED";
        }
        long roots=commentMapper.rootCount(work.getId()); int pages=roots==0?0:(int)((roots+19)/20);
        return new InteractionViews.CommentMutation(target.getCommentId(),rootPublic,outcome,
                commentMapper.activeCount(work.getId()),replies,roots,pages,true);
    }

    private PhotoComment insert(PhotoWork work,Long accountId,Long rootId,String raw){
        PhotoComment c=new PhotoComment();c.setCommentId(ids.next());c.setWorkId(work.getId());c.setAuthorAccountId(accountId);
        c.setRootCommentId(rootId);c.setContent(content(raw));c.setDisplayState("ACTIVE");c.setCreatedAt(now());c.setRowVersion(0L);
        commentMapper.insert(c);return c;
    }
    private String content(String raw){String v=UnicodeText.nfc(UnicodeText.trimUnicode(raw.replace("\r\n","\n")));
        if(v.isEmpty()||UnicodeText.graphemeCount(v)>1000||UnicodeText.containsForbiddenControl(v,true))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);return v;}
    private InteractionViews.ThreadItem thread(PhotoComment c,Long viewer){
        List<PhotoComment> rows=commentMapper.firstReplies(c.getId(),4);boolean more=rows.size()>3;
        List<PhotoComment> selected=more?rows.subList(0,3):rows;long count=commentMapper.activeReplyCount(c.getId());
        boolean active="ACTIVE".equals(c.getDisplayState());
        return new InteractionViews.ThreadItem(c.getCommentId(),c.getDisplayState(),active?author(c.getAuthorAccountId()):null,
                active?c.getContent():null,c.getCreatedAt().atOffset(ZoneOffset.UTC),active,
                active&&c.getAuthorAccountId().equals(viewer),selected.stream().map(r->reply(r,viewer)).toList(),count,more,
                more?encode(c.getCommentId(),selected.get(selected.size()-1)):null);
    }
    private InteractionViews.Reply reply(PhotoComment c,Long viewer){return new InteractionViews.Reply(c.getCommentId(),author(c.getAuthorAccountId()),c.getContent(),c.getCreatedAt().atOffset(ZoneOffset.UTC),c.getAuthorAccountId().equals(viewer));}
    private InteractionViews.Author author(Long id){Account a=accountMapper.selectById(id);UserProfile p=profileMapper.selectById(id);return new InteractionViews.Author(a.getUid(),p.getUsername());}
    private PhotoWork published(String id){PhotoWork w=workMapper.findByPublicId(id);if(w==null||!"PUBLISHED".equals(w.getPublicationState()))throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return w;}
    private PhotoWork publishedLocked(String id){PhotoWork w=workMapper.findByPublicIdForUpdate(id);if(w==null||!"PUBLISHED".equals(w.getPublicationState()))throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return w;}
    private String encode(String root,PhotoComment c){String p=root+"|"+c.getCreatedAt()+"|"+c.getCommentId();return Base64.getUrlEncoder().withoutPadding().encodeToString(p.getBytes(StandardCharsets.UTF_8))+"."+crypto.hmac(p);}
    private Cursor decode(String token,String root){if(token==null||token.isBlank())return new Cursor(LocalDateTime.of(1970,1,1,0,0),"");try{int d=token.lastIndexOf('.');String p=new String(Base64.getUrlDecoder().decode(token.substring(0,d)),StandardCharsets.UTF_8);if(!crypto.constantTimeEquals(crypto.hmac(p),token.substring(d+1)))throw new Exception();String[] x=p.split("\\|",3);if(!x[0].equals(root))throw new Exception();return new Cursor(LocalDateTime.parse(x[1]),x[2]);}catch(Exception e){throw new BusinessException(ErrorCode.INVALID_CURSOR);}}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);} private record Cursor(LocalDateTime at,String commentId){}
}

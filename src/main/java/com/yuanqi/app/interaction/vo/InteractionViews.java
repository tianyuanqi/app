package com.yuanqi.app.interaction.vo;

import com.yuanqi.app.common.api.PageResult;
import java.time.OffsetDateTime;
import java.util.List;

public final class InteractionViews {
    private InteractionViews() {}
    public record LikeMutation(boolean liked, long likeCount, long receivedLikeCount) {}
    public record Author(String uid, String username) {}
    public record Reply(String commentId, Author author, String content, OffsetDateTime createdAt, boolean canDelete) {}
    public record ThreadItem(String commentId, String displayState, Author author, String content,
                             OffsetDateTime createdAt, boolean canReply, boolean canDelete,
                             List<Reply> previewReplies, long replyCount, boolean hasMoreReplies,
                             String replyContinuationCursor) {}
    public record CommentPage(List<ThreadItem> items, int page, int pageSize, long totalItems, int totalPages,
                              boolean hasPrevious, boolean hasNext, long commentCount) {}
    public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}
    public record CommentCreate(ThreadItem comment, long commentCount) {}
    public record ReplyCreate(Reply reply, long commentCount, long replyCount) {}
    public record CommentMutation(String deletedCommentId, String rootCommentId, String outcome,
                                  long commentCount, long rootReplyCount, long rootTotalItems,
                                  int rootTotalPages, boolean rootPageInvalidated) {}
}

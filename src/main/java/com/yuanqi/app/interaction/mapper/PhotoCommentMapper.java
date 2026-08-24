package com.yuanqi.app.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.interaction.entity.PhotoComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PhotoCommentMapper extends BaseMapper<PhotoComment> {
    @Select("SELECT * FROM photo_comment WHERE comment_id=#{commentId} LIMIT 1 FOR UPDATE")
    PhotoComment findForUpdate(@Param("commentId") String commentId);
    @Select("SELECT COUNT(*) FROM photo_comment WHERE work_id=#{workId} AND display_state='ACTIVE'")
    long activeCount(@Param("workId") Long workId);
    @Select("SELECT COUNT(*) FROM photo_comment WHERE root_comment_id=#{rootId} AND display_state='ACTIVE'")
    long activeReplyCount(@Param("rootId") Long rootId);
    @Select("SELECT COUNT(*) FROM photo_comment WHERE work_id=#{workId} AND root_comment_id IS NULL")
    long rootCount(@Param("workId") Long workId);
    @Select("SELECT * FROM photo_comment WHERE work_id=#{workId} AND root_comment_id IS NULL " +
            "ORDER BY created_at,comment_id LIMIT #{limit} OFFSET #{offset}")
    List<PhotoComment> roots(@Param("workId") Long workId, @Param("offset") long offset, @Param("limit") int limit);
    @Select("SELECT * FROM photo_comment WHERE root_comment_id=#{rootId} AND display_state='ACTIVE' " +
            "ORDER BY created_at,comment_id LIMIT #{limit}")
    List<PhotoComment> firstReplies(@Param("rootId") Long rootId, @Param("limit") int limit);
    @Select("SELECT * FROM photo_comment WHERE root_comment_id=#{rootId} AND display_state='ACTIVE' " +
            "AND (created_at>#{at} OR (created_at=#{at} AND comment_id>#{commentId})) " +
            "ORDER BY created_at,comment_id LIMIT #{limit}")
    List<PhotoComment> repliesAfter(@Param("rootId") Long rootId, @Param("at") LocalDateTime at,
                                    @Param("commentId") String commentId, @Param("limit") int limit);
    @Delete("DELETE FROM photo_comment WHERE id=#{id}")
    int hardDelete(@Param("id") Long id);
}

package com.yuanqi.app.photo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品卡片 VO：用于发现流、个人主页作品墙等列表场景。
 * <p>刻意不含完整 EXIF，保持列表轻量。</p>
 */
@Data
@Schema(description = "作品卡片")
public class PhotoCardVO {

    private Long id;
    private String title;
    private String imageUrl;
    /** 缩略图，当前与原图相同，预留多规格扩展 */
    private String thumbUrl;
    private String location;
    private String status;
    private LocalDateTime createTime;
    /** 互动未落地前固定为 0，字段先占位 */
    private Integer likeCount;
    private Integer favoriteCount;
    private Author author;
    private Category category;

    @Data
    public static class Author {
        private String uid;
        private String username;
        private String avatarUrl;
    }

    @Data
    public static class Category {
        private Long id;
        private String name;
    }
}

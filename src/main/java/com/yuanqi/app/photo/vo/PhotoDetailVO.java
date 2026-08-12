package com.yuanqi.app.photo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品详情响应，组合作者、分类、标签和拍摄参数。
 */
@Data
@Schema(description = "作品详情")
public class PhotoDetailVO {
    private Long id;
    private String title;
    private String description;
    private String imageUrl;
    private String location;
    private LocalDateTime createTime;
    private LocalDateTime shootDate;
    private String cameraBody;
    private String lens;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    /** 作品状态 */
    private String status;
    private String rejectReason;
    private Author author;
    private Category category;
    private List<Tag> tags;

    @Data
    public static class Author {
        private String uid;
        private String username;
    }

    @Data
    public static class Category {
        private Long id;
        private String name;
    }

    @Data
    public static class Tag {
        private Long id;
        private String name;
    }
}

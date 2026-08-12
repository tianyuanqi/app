package com.yuanqi.app.photo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品接口请求参数。
 */
public final class PhotoRequests {

    private PhotoRequests() {
    }

    /**
     * 上传请求：表单字段名保持与前端兼容（category / tag）。
     */
    @Data
    public static class Upload {
        @NotNull(message = "文件不能为空")
        private MultipartFile file;

        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过100个字符")
        private String title;

        @Size(max = 2000, message = "描述不能超过2000个字符")
        private String description;

        @Size(max = 100, message = "拍摄位置不能超过100个字符")
        private String location;

        @NotNull(message = "分类不能为空")
        @Min(value = 1, message = "分类ID必须大于0")
        private Integer category;

        private List<@NotBlank(message = "标签不能为空") String> tag;
    }

    @Data
    public static class Update {
        @Size(max = 100, message = "标题不能超过100个字符")
        private String title;

        @Size(max = 2000, message = "描述不能超过2000个字符")
        private String description;

        @Size(max = 100, message = "拍摄位置不能超过100个字符")
        private String location;

        @Min(value = 1, message = "分类ID必须大于0")
        private Integer categoryId;

        private List<@NotBlank(message = "标签不能为空") String> tags;
    }

    @Data
    public static class Search {
        @Min(value = 1, message = "页码必须大于0")
        private Integer current = 1;

        @Min(value = 1, message = "每页数量必须大于0")
        private Integer pageSize = 10;

        private String keyword;
        private Long authorId;
        private String author;
        private Long categoryId;
        private Long tagId;
        private String tag;
        private String location;
        private String cameraBody;
        private Integer isoMin;
        private Integer isoMax;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime shootFrom;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime shootTo;

        private String sort = "latest";

        /** 限制单页最大 100 条，防止过大分页 */
        public int safePageSize() {
            return Math.min(pageSize == null ? 10 : pageSize, 100);
        }
    }
}

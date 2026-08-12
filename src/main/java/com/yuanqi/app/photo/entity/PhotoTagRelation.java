package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作品与标签多对多关联表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "作品标签关联")
@TableName("t_photo_tag")
public class PhotoTagRelation {

    private Long photoId;
    private Long tagId;
}

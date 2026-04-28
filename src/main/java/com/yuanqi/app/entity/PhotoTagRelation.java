package com.yuanqi.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 照片和标签的中间表，一张照片对应多个标签
 */

@Schema(description = "关联照片和标签的中间表实体")
@Data
@TableName("t_photo_tag")
@NoArgsConstructor
@AllArgsConstructor
public class PhotoTagRelation {

    @Schema(description = "照片id")
    private Long photoId;

    @Schema(description = "标签id")
    private Long tagId;

}

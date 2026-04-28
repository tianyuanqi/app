package com.yuanqi.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "照片标签实体")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("photo_tag")
public class PhotoTag {

    @Schema(description = "照片标签实体唯一id",example = "1")
    @TableId(type = IdType.AUTO) // 必须加上这个！
    private Long id;

    @Schema(description = "照片标签名称",example = "4k分辨率")
    private String name;

}

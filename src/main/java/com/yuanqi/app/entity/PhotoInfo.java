package com.yuanqi.app.entity; // 请把 com.example.app 替换为您自己的实际包名

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

@Data // Lombok 注解，自动生成 Getter/Setter，保持代码整洁
@Schema(description = "照片详细信息实体")
@TableName("photo_info") // 告诉 MyBatis-Plus 这个类对应哪张表
public class PhotoInfo {

    @Schema(description = "照片唯一ID")
    @TableId(type = IdType.AUTO) // 标识这是主键，且为自增
    private Long id;

    // 👇 新增：绑定用户的核心字段！记录是谁上传了这张照片
    @Schema(description = "上传者的真实内部 ID")
    private Long userId;

    @Schema(description = "照片标题")
    private String title;

    @Schema(description = "照片描述")
    private String description;

    @Schema(description = "图片URL访问路径", example = "/uploads/1713686171000_test.jpg")
    private String imageUrl;

    @Schema(description = "拍摄机身", example = "Nikon Z8")
    private String cameraBody;

    @Schema(description = "镜头", example = "Nikkor 24-70 f2.8s")
    private String lens;

    @Schema(description = "焦段", example = "50mm")
    private String focalLength;

    @Schema(description = "光圈值", example = "f/2.8")
    private String aperture;

    @Schema(description = "快门速度", example = "1/50")
    private String shutterSpeed;

    @Schema(description = "感光度", example = "100")
    private Integer iso;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "拍摄时间")
    private LocalDateTime shoot_date;
}
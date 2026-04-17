package com.yuanqi.app.entity; // 请把 com.example.app 替换为您自己的实际包名

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data // Lombok 注解，自动生成 Getter/Setter，保持代码整洁
@TableName("photo_info") // 告诉 MyBatis-Plus 这个类对应哪张表
public class PhotoInfo {

    @TableId(type = IdType.AUTO) // 标识这是主键，且为自增
    private Long id;

    private String title;
    private String description;
    private String imageUrl;
    private String cameraBody;
    private String lens;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    private LocalDateTime createTime;
}
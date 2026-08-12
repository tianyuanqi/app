package com.yuanqi.app.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地图片上传目录配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 物理存储目录，对应环境变量 APP_UPLOAD_DIR */
    private String dir = "./uploads";
}

package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 对象存储配置（兼容 MinIO / 阿里云 OSS / AWS S3 / 腾讯云 COS）
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {

    /** S3 端点，如 http://minio:9000 或 https://oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** 区域，如 us-east-1（MinIO 可填任意值） */
    private String region = "us-east-1";

    /** Bucket 名称 */
    private String bucket;

    /** Access Key */
    private String accessKey;

    /** Secret Key */
    private String secretKey;

    /** 是否使用路径风格访问（MinIO 必须 true，AWS S3 默认 false） */
    private boolean pathStyleEnabled = true;
}

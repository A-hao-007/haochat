package com.ahao.haochat.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 是否开启
     */
    Boolean enabled;

    /**
     * 存储对象服务器类型
     */
    OssType type;

    /**
     * OSS 访问端点（服务端内部访问，如 http://minio:9000）
     */
    String endpoint;

    /**
     * OSS 对外公网端点（浏览器可访问，如 https://minio.ahao.space）。
     * 用于生成「预签名上传 URL」与「下载 URL」，留空时回退到 {@link #endpoint}。
     */
    String publicEndpoint;

    /**
     * 用户名
     */
    String accessKey;

    /**
     * 密码
     */
    String secretKey;

    /**
     * 存储桶
     */
    String bucketName;
}

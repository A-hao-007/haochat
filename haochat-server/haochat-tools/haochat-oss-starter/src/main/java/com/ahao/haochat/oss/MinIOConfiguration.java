package com.ahao.haochat.oss;

import cn.hutool.core.util.StrUtil;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
public class MinIOConfiguration {


    @Bean
    @SneakyThrows
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(OssProperties ossProperties) {
        return MinioClient.builder()
                .endpoint(ossProperties.getEndpoint())
                .credentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnBean({MinioClient.class})
    @ConditionalOnMissingBean(MinIOTemplate.class)
    public MinIOTemplate minioTemplate(MinioClient minioClient, OssProperties ossProperties) {
        // 预签名 / 下载 URL 必须使用公网端点，浏览器才能访问；
        // 显式指定 region 以避免预签名时回源拉取 region 的网络请求。
        String publicEndpoint = StrUtil.blankToDefault(ossProperties.getPublicEndpoint(), ossProperties.getEndpoint());
        MinioClient presignClient = MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())
                .region("us-east-1")
                .build();
        return new MinIOTemplate(minioClient, presignClient, ossProperties, publicEndpoint);
    }
}
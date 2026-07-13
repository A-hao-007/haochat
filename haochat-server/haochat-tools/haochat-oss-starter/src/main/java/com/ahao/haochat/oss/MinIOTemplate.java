package com.ahao.haochat.oss;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import com.ahao.haochat.oss.domain.OssReq;
import com.ahao.haochat.oss.domain.OssResp;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
public class MinIOTemplate {

    /**
     * MinIO 客户端（服务端内部访问，如 http://minio:9000）
     */
    MinioClient minioClient;

    /**
     * 预签名专用客户端（绑定公网端点，签名 Host 与浏览器访问一致）
     */
    MinioClient presignClient;

    /**
     * MinIO 配置类
     */
    OssProperties ossProperties;

    /**
     * 公网端点，用于拼接下载 URL
     */
    String publicEndpoint;

    /**
     * 查询所有存储桶
     *
     * @return Bucket 集合
     */
    @SneakyThrows
    public List<Bucket> listBuckets() {
        return minioClient.listBuckets();
    }

    /**
     * 桶是否存在
     *
     * @param bucketName 桶名
     * @return 是否存在
     */
    @SneakyThrows
    public boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void makeBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 删除一个空桶 如果存储桶存在对象不为空时，删除会报错。
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void removeBucket(String bucketName) {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    /**
     * 返回临时带签名、过期时间一天、PUT请求方式的访问URL
     */
    @SneakyThrows
    public OssResp getPreSignedObjectUrl(OssReq req) {
        String absolutePath = req.isAutoPath() ? generateAutoPath(req) : req.getFilePath() + StrUtil.SLASH + req.getFileName();
        // 去掉前导斜杠：否则对象键变成 "/chat/..."，预签名 URL 出现 "//"，与下载 URL 的键不一致导致下载 404
        absolutePath = StrUtil.removePrefix(absolutePath, StrUtil.SLASH);
        String url = presignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(ossProperties.getBucketName())
                        .object(absolutePath)
                        .expiry(60 * 60 * 24)
                        .build());
        return OssResp.builder()
                .uploadUrl(url)
                .downloadUrl(getDownloadUrl(ossProperties.getBucketName(), absolutePath))
                .build();
    }

    private String getDownloadUrl(String bucket, String pathFile) {
        // pathFile 已去除前导斜杠，这里显式补一个斜杠，保证与预签名对象键完全一致
        return publicEndpoint + StrUtil.SLASH + bucket + StrUtil.SLASH + StrUtil.removePrefix(pathFile, StrUtil.SLASH);
    }

    /**
     * GetObject接口用于获取某个文件（Object）。此操作需要对此Object具有读权限。
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     */
    @SneakyThrows
    public InputStream getObject(String bucketName, String ossFilePath) {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossFilePath).build());
    }

    @SneakyThrows
    public InputStream getObject(String objectKey) {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(ossProperties.getBucketName()).object(objectKey).build());
    }

    @SneakyThrows
    public void putObject(String objectKey, InputStream inputStream, long size, String contentType) {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(ossProperties.getBucketName())
                .object(objectKey)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build());
    }

    @SneakyThrows
    public String getPresignedUploadUrl(String objectKey, int expirySeconds) {
        return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT).bucket(ossProperties.getBucketName()).object(objectKey)
                .expiry(expirySeconds, TimeUnit.SECONDS).build());
    }

    @SneakyThrows
    public String getPresignedDownloadUrl(String objectKey, int expirySeconds) {
        return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(ossProperties.getBucketName()).object(objectKey)
                .expiry(expirySeconds, TimeUnit.SECONDS).build());
    }

    public String getPublicObjectUrl(String objectKey) {
        return getDownloadUrl(ossProperties.getBucketName(), objectKey);
    }

    @SneakyThrows
    public StatObjectResponse statObject(String objectKey) {
        return minioClient.statObject(StatObjectArgs.builder().bucket(ossProperties.getBucketName()).object(objectKey).build());
    }

    @SneakyThrows
    public void removeObject(String objectKey) {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(ossProperties.getBucketName()).object(objectKey).build());
    }

    /**
     * 查询桶的对象信息
     *
     * @param bucketName 桶名
     * @param recursive  是否递归查询
     * @return
     */
    @SneakyThrows
    public Iterable<Result<Item>> listObjects(String bucketName, boolean recursive) {
        return minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(recursive).build());
    }

    /**
     * 生成随机文件名，防止重复
     *
     * @return
     */
    public String generateAutoPath(OssReq req) {
        String uid = Optional.ofNullable(req.getUid()).map(String::valueOf).orElse("000000");
        cn.hutool.core.lang.UUID uuid = cn.hutool.core.lang.UUID.fastUUID();
        String suffix = FileNameUtil.getSuffix(req.getFileName());
        String yearAndMonth = DateUtil.format(new Date(), DatePattern.NORM_MONTH_PATTERN);
        return req.getFilePath() + StrUtil.SLASH + yearAndMonth + StrUtil.SLASH + uid + StrUtil.SLASH + uuid + StrUtil.DOT + suffix;
    }

    /**
     * 获取带签名的临时上传元数据对象，前端可获取后，直接上传到Minio
     *
     * @param bucketName
     * @param fileName
     * @return
     */
    @SneakyThrows
    public Map<String, String> getPreSignedPostFormData(String bucketName, String fileName) {
        // 为存储桶创建一个上传策略，过期时间为7天
        PostPolicy policy = new PostPolicy(bucketName, ZonedDateTime.now().plusDays(7));
        // 设置一个参数key，值为上传对象的名称
        policy.addEqualsCondition("key", fileName);
        // 添加Content-Type以"image/"开头，表示只能上传照片
        policy.addStartsWithCondition("Content-Type", "image/");
        // 设置上传文件的大小 64kiB to 10MiB.
        policy.addContentLengthRangeCondition(64 * 1024, 10 * 1024 * 1024);
        return minioClient.getPresignedPostFormData(policy);
    }

}

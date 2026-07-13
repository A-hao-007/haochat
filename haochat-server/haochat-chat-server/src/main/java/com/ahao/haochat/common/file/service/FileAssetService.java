package com.ahao.haochat.common.file.service;

import cn.hutool.core.io.file.FileNameUtil;
import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.event.FileUploadCompletedEvent;
import com.ahao.haochat.common.file.dao.FileAssetDao;
import com.ahao.haochat.common.file.domain.FileAsset;
import com.ahao.haochat.common.file.domain.FileScene;
import com.ahao.haochat.common.file.web.FileAssetStatusResp;
import com.ahao.haochat.common.file.web.FileUploadInitReq;
import com.ahao.haochat.common.file.web.FileUploadTicket;
import com.ahao.haochat.common.user.service.UserService;
import com.ahao.haochat.oss.MinIOTemplate;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileAssetService {
    public static final int PENDING = 0;
    public static final int AVAILABLE = 1;
    public static final int FAILED = 2;
    public static final int DELETED = 3;

    private static final int UPLOAD_EXPIRE_SECONDS = 900;
    private static final int DOWNLOAD_EXPIRE_SECONDS = 600;

    private final FileAssetDao assetDao;
    private final ContactDao contactDao;
    private final UserService userService;
    private final MinIOTemplate minio;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public FileUploadTicket init(Long uid, FileUploadInitReq req) {
        FileScene scene = FileScene.of(req.getScene());
        AssertUtil.isNotEmpty(scene, "invalid upload scene");
        AssertUtil.isTrue(req.getSize() <= scene.maxSize, "file exceeds scene size limit");
        AssertUtil.isTrue(validType(scene, req.getContentType(), req.getFileName()), "unsupported file type");
        if (scene == FileScene.CHAT_IMAGE || scene == FileScene.CHAT_FILE) {
            AssertUtil.isNotEmpty(req.getRoomId(), "chat attachment requires roomId");
            AssertUtil.isNotEmpty(contactDao.get(uid, req.getRoomId()), "no permission to upload to this room");
        }

        String ext = StringUtils.defaultIfBlank(FileNameUtil.getSuffix(req.getFileName()), "bin")
                .toLowerCase(Locale.ROOT);
        String key = "asset/" + scene.code + "/" + uid + "/" + UUID.randomUUID() + "." + ext;

        FileAsset asset = new FileAsset();
        asset.setUid(uid);
        asset.setRoomId(req.getRoomId());
        asset.setScene(scene.code);
        asset.setObjectKey(key);
        asset.setOriginalName(req.getFileName());
        asset.setContentType(req.getContentType());
        asset.setSize(req.getSize());
        asset.setStatus(PENDING);
        asset.setVirusStatus(0);
        asset.setExpireTime(new Date(System.currentTimeMillis() + UPLOAD_EXPIRE_SECONDS * 1000L));
        assetDao.save(asset);

        return FileUploadTicket.builder()
                .assetId(asset.getId())
                .status(PENDING)
                .uploadUrl(minio.getPresignedUploadUrl(key, UPLOAD_EXPIRE_SECONDS))
                .expiresInSeconds((long) UPLOAD_EXPIRE_SECONDS)
                .build();
    }

    @Transactional
    public String complete(Long uid, Long assetId) {
        FileAsset asset = owned(uid, assetId);
        if (Objects.equals(asset.getStatus(), AVAILABLE)) {
            return downloadUrl(asset);
        }
        AssertUtil.equal(asset.getStatus(), PENDING, "file status cannot be confirmed");
        try {
            StatObjectResponse stat = minio.statObject(asset.getObjectKey());
            AssertUtil.equal(stat.size(), asset.getSize(), "uploaded file size mismatch");
            AssertUtil.isTrue(StringUtils.isBlank(stat.contentType())
                            || stat.contentType().equalsIgnoreCase(asset.getContentType()),
                    "uploaded file content type mismatch");
        } catch (Exception e) {
            assetDao.markFailed(asset.getId(), new Date());
            try {
                minio.removeObject(asset.getObjectKey());
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("upload confirm failed");
        }
        boolean marked = assetDao.markAvailable(asset.getId(), new Date());
        FileAsset latest = assetDao.getById(asset.getId());
        if (marked) {
            applicationEventPublisher.publishEvent(new FileUploadCompletedEvent(this, latest.getId()));
            return downloadUrl(latest);
        }
        if (Objects.equals(latest.getStatus(), AVAILABLE)) {
            return downloadUrl(latest);
        }
        throw new IllegalArgumentException("file status changed during confirm");
    }

    public FileUploadTicket retry(Long uid, Long assetId) {
        FileAsset asset = owned(uid, assetId);
        if (Objects.equals(asset.getStatus(), AVAILABLE)) {
            return FileUploadTicket.builder()
                    .assetId(asset.getId())
                    .status(AVAILABLE)
                    .downloadUrl(downloadUrl(asset))
                    .expiresInSeconds((long) DOWNLOAD_EXPIRE_SECONDS)
                    .build();
        }
        AssertUtil.notEqual(asset.getStatus(), DELETED, "file upload has been canceled");
        AssertUtil.isFalse(Objects.equals(asset.getStatus(), FAILED) && Objects.equals(asset.getVirusStatus(), 2),
                "unsafe file cannot be retried");
        Date expireTime = new Date(System.currentTimeMillis() + UPLOAD_EXPIRE_SECONDS * 1000L);
        boolean renewed = assetDao.renewUpload(asset.getId(), expireTime, new Date());
        FileAsset latest = assetDao.getById(asset.getId());
        if (!renewed && Objects.equals(latest.getStatus(), AVAILABLE)) {
            return FileUploadTicket.builder()
                    .assetId(asset.getId())
                    .status(AVAILABLE)
                    .downloadUrl(downloadUrl(latest))
                    .expiresInSeconds((long) DOWNLOAD_EXPIRE_SECONDS)
                    .build();
        }
        AssertUtil.isTrue(renewed || Objects.equals(latest.getStatus(), PENDING), "file status cannot be retried");
        return FileUploadTicket.builder()
                .assetId(latest.getId())
                .status(PENDING)
                .uploadUrl(minio.getPresignedUploadUrl(latest.getObjectKey(), UPLOAD_EXPIRE_SECONDS))
                .expiresInSeconds((long) UPLOAD_EXPIRE_SECONDS)
                .build();
    }

    public FileAssetStatusResp status(Long uid, Long assetId) {
        FileAsset asset = owned(uid, assetId);
        return FileAssetStatusResp.builder()
                .assetId(asset.getId())
                .scene(asset.getScene())
                .status(asset.getStatus())
                .roomId(asset.getRoomId())
                .fileName(asset.getOriginalName())
                .contentType(asset.getContentType())
                .size(asset.getSize())
                .expireTime(asset.getExpireTime())
                .downloadUrl(Objects.equals(asset.getStatus(), AVAILABLE) ? downloadUrl(asset) : null)
                .build();
    }

    public void cancel(Long uid, Long assetId) {
        FileAsset asset = owned(uid, assetId);
        if (Objects.equals(asset.getStatus(), DELETED)) {
            return;
        }
        AssertUtil.notEqual(asset.getStatus(), AVAILABLE, "available file cannot be canceled");
        boolean marked = assetDao.markDeleted(asset.getId(), new Date());
        FileAsset latest = assetDao.getById(asset.getId());
        if (!marked && Objects.equals(latest.getStatus(), AVAILABLE)) {
            throw new IllegalArgumentException("available file cannot be canceled");
        }
        try {
            minio.removeObject(asset.getObjectKey());
        } catch (Exception ignored) {
        }
    }

    public String download(Long uid, Long assetId) {
        FileAsset asset = requireAvailable(assetId);
        FileScene scene = sceneOf(asset);
        boolean allowed = scene.publicRead
                || Objects.equals(asset.getUid(), uid)
                || (asset.getRoomId() != null && contactDao.get(uid, asset.getRoomId()) != null);
        AssertUtil.isTrue(allowed, "no permission to access this file");
        return downloadUrl(asset);
    }

    public String bindAvatar(Long uid, Long assetId) {
        FileAsset asset = owned(uid, assetId);
        AssertUtil.equal(FileScene.AVATAR.code, asset.getScene(), "asset is not avatar scene");
        AssertUtil.equal(asset.getStatus(), AVAILABLE, "avatar file is not available");
        String avatarUrl = minio.getPublicObjectUrl(asset.getObjectKey());
        userService.updateAvatar(uid, avatarUrl);
        return avatarUrl;
    }

    public FileAsset assertAttachable(Long uid, Long roomId, Long assetId, FileScene expectedScene) {
        AssertUtil.isNotEmpty(assetId, "assetId required");
        FileAsset asset = owned(uid, assetId);
        AssertUtil.equal(asset.getStatus(), AVAILABLE, "file is not available");
        AssertUtil.equal(expectedScene.code, asset.getScene(), "file scene mismatch");
        AssertUtil.equal(roomId, asset.getRoomId(), "file room mismatch");
        AssertUtil.isNotEmpty(contactDao.get(uid, roomId), "no permission to attach file in this room");
        return asset;
    }

    private String downloadUrl(FileAsset asset) {
        FileScene scene = sceneOf(asset);
        return scene.publicRead
                ? minio.getPublicObjectUrl(asset.getObjectKey())
                : minio.getPresignedDownloadUrl(asset.getObjectKey(), DOWNLOAD_EXPIRE_SECONDS);
    }

    public FileAsset requireAvailable(Long assetId) {
        FileAsset asset = assetDao.getById(assetId);
        AssertUtil.isNotEmpty(asset, "file not found");
        AssertUtil.equal(asset.getStatus(), AVAILABLE, "file is not available");
        return asset;
    }

    public FileAsset owned(Long uid, Long id) {
        FileAsset asset = assetDao.getById(id);
        AssertUtil.isNotEmpty(asset, "file not found");
        AssertUtil.equal(asset.getUid(), uid, "no permission to operate this file");
        return asset;
    }

    private FileScene sceneOf(FileAsset asset) {
        FileScene scene = FileScene.of(asset.getScene());
        AssertUtil.isNotEmpty(scene, "invalid file scene");
        return scene;
    }

    private boolean validType(FileScene scene, String type, String name) {
        if (StringUtils.isBlank(type)) {
            return false;
        }
        if (scene.imageOnly) {
            return type.startsWith("image/");
        }
        String lower = StringUtils.defaultString(name).toLowerCase(Locale.ROOT);
        return !lower.matches(".*\\.(exe|bat|cmd|com|js|jar|sh|ps1)$")
                && !type.startsWith("application/x-msdownload");
    }

    @Scheduled(fixedDelay = 600000)
    public void cleanupPending() {
        Date now = new Date();
        for (FileAsset asset : assetDao.lambdaQuery()
                .eq(FileAsset::getStatus, PENDING)
                .lt(FileAsset::getExpireTime, now)
                .last("limit 100")
                .list()) {
            try {
                minio.removeObject(asset.getObjectKey());
            } catch (Exception ignored) {
            }
            asset.setStatus(DELETED);
            assetDao.updateById(asset);
        }
    }
}

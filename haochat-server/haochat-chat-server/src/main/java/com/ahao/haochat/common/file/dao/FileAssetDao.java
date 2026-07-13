package com.ahao.haochat.common.file.dao;

import com.ahao.haochat.common.file.domain.FileAsset;
import com.ahao.haochat.common.file.mapper.FileAssetMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;

@Service
public class FileAssetDao extends ServiceImpl<FileAssetMapper, FileAsset> {
    public boolean markAvailable(Long assetId, Date updateTime) {
        return lambdaUpdate()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.PENDING)
                .set(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.AVAILABLE)
                .set(FileAsset::getExpireTime, null)
                .set(FileAsset::getUpdateTime, updateTime)
                .update();
    }

    public boolean markFailed(Long assetId, Date updateTime) {
        return lambdaUpdate()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.PENDING)
                .set(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.FAILED)
                .set(FileAsset::getUpdateTime, updateTime)
                .update();
    }

    public boolean renewUpload(Long assetId, Date expireTime, Date updateTime) {
        return lambdaUpdate()
                .eq(FileAsset::getId, assetId)
                .in(FileAsset::getStatus, Arrays.asList(
                        com.ahao.haochat.common.file.service.FileAssetService.PENDING,
                        com.ahao.haochat.common.file.service.FileAssetService.FAILED))
                .set(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.PENDING)
                .set(FileAsset::getExpireTime, expireTime)
                .set(FileAsset::getUpdateTime, updateTime)
                .update();
    }

    public boolean markDeleted(Long assetId, Date updateTime) {
        return lambdaUpdate()
                .eq(FileAsset::getId, assetId)
                .in(FileAsset::getStatus, Arrays.asList(
                        com.ahao.haochat.common.file.service.FileAssetService.PENDING,
                        com.ahao.haochat.common.file.service.FileAssetService.FAILED,
                        com.ahao.haochat.common.file.service.FileAssetService.DELETED))
                .set(FileAsset::getStatus, com.ahao.haochat.common.file.service.FileAssetService.DELETED)
                .set(FileAsset::getUpdateTime, updateTime)
                .update();
    }
}

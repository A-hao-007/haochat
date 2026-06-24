package com.ahao.haochat.common.user.service.impl;

import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.user.domain.enums.OssSceneEnum;
import com.ahao.haochat.common.user.domain.vo.request.oss.UploadUrlReq;
import com.ahao.haochat.common.user.service.OssService;
import com.ahao.haochat.oss.MinIOTemplate;
import com.ahao.haochat.oss.domain.OssReq;
import com.ahao.haochat.oss.domain.OssResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-06-20
 */
@Service
@ConditionalOnExpression("${oss.enabled}")
public class OssServiceImpl implements OssService {
    @Autowired
    private MinIOTemplate minIOTemplate;

    @Override
    public OssResp getUploadUrl(Long uid, UploadUrlReq req) {
        OssSceneEnum sceneEnum = OssSceneEnum.of(req.getScene());
        AssertUtil.isNotEmpty(sceneEnum, "场景有误");
        OssReq ossReq = OssReq.builder()
                .fileName(req.getFileName())
                .filePath(sceneEnum.getPath())
                .uid(uid)
                .build();
        return minIOTemplate.getPreSignedObjectUrl(ossReq);
    }
}

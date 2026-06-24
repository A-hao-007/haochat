package com.ahao.haochat.common.user.service;

import com.ahao.haochat.common.user.domain.vo.request.oss.UploadUrlReq;
import com.ahao.haochat.oss.domain.OssResp;

/**
 * <p>
 * oss 服务类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
public interface OssService {

    /**
     * 获取临时的上传链接
     */
    OssResp getUploadUrl(Long uid, UploadUrlReq req);
}

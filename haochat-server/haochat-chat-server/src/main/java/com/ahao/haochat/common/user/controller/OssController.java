package com.ahao.haochat.common.user.controller;

import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.vo.request.oss.UploadUrlReq;
import com.ahao.haochat.common.user.service.OssService;
import com.ahao.haochat.oss.domain.OssResp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Description: oss控制层
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-06-20
 */
@RestController
@RequestMapping("/capi/oss")
@Api(tags = "oss相关接口")
@ConditionalOnExpression("${oss.enabled}")
public class OssController {
    @Autowired
    private OssService ossService;

    @GetMapping("/upload/url")
    @ApiOperation("获取临时上传链接")
    public ApiResult<OssResp> getUploadUrl(@Valid UploadUrlReq req) {
        return ApiResult.success(ossService.getUploadUrl(RequestHolder.get().getUid(), req));
    }
}

package com.ahao.haochat.common.file.controller;

import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.file.service.FileAssetService;
import com.ahao.haochat.common.file.web.FileAssetStatusResp;
import com.ahao.haochat.common.file.web.FileUploadCompleteReq;
import com.ahao.haochat.common.file.web.FileUploadInitReq;
import com.ahao.haochat.common.file.web.FileUploadTicket;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/capi/file")
@RequiredArgsConstructor
public class FileAssetController {
    private final FileAssetService service;

    @PostMapping("/upload/init")
    public ApiResult<FileUploadTicket> init(@Valid @RequestBody FileUploadInitReq req) {
        return ApiResult.success(service.init(RequestHolder.get().getUid(), req));
    }

    @PostMapping("/upload/complete")
    public ApiResult<Map<String, Object>> complete(@Valid @RequestBody FileUploadCompleteReq req) {
        String url = service.complete(RequestHolder.get().getUid(), req.getAssetId());
        return ApiResult.success(Map.of("assetId", req.getAssetId(), "downloadUrl", url));
    }

    @PostMapping("/upload/{assetId}/retry")
    public ApiResult<FileUploadTicket> retry(@PathVariable Long assetId) {
        return ApiResult.success(service.retry(RequestHolder.get().getUid(), assetId));
    }

    @GetMapping("/upload/{assetId}/status")
    public ApiResult<FileAssetStatusResp> status(@PathVariable Long assetId) {
        return ApiResult.success(service.status(RequestHolder.get().getUid(), assetId));
    }

    @DeleteMapping("/upload/{assetId}")
    public ApiResult<Void> cancel(@PathVariable Long assetId) {
        service.cancel(RequestHolder.get().getUid(), assetId);
        return ApiResult.success();
    }

    @PutMapping("/avatar/{assetId}")
    public ApiResult<Map<String, String>> bindAvatar(@PathVariable Long assetId) {
        String avatarUrl = service.bindAvatar(RequestHolder.get().getUid(), assetId);
        return ApiResult.success(Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/{id}/download")
    public ApiResult<Map<String, String>> download(@PathVariable Long id) {
        return ApiResult.success(Map.of("downloadUrl", service.download(RequestHolder.get().getUid(), id)));
    }
}

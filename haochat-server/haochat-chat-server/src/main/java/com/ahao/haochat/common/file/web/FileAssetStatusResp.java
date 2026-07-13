package com.ahao.haochat.common.file.web;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class FileAssetStatusResp {
    private Long assetId;
    private String scene;
    private Integer status;
    private Long roomId;
    private String fileName;
    private String contentType;
    private Long size;
    private Date expireTime;
    private String downloadUrl;
}

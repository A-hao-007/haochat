package com.ahao.haochat.common.file.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadTicket {
    private Long assetId;
    private Integer status;
    private String uploadUrl;
    private String downloadUrl;
    private Long expiresInSeconds;
}

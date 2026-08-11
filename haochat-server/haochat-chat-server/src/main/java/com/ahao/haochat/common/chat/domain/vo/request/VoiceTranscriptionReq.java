package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class VoiceTranscriptionReq {
    @NotNull
    @ApiModelProperty("语音附件 assetId")
    private Long assetId;
}

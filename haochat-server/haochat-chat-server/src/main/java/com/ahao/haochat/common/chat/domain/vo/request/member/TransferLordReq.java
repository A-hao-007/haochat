package com.ahao.haochat.common.chat.domain.vo.request.member;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 转让群主请求
 */
@Data
public class TransferLordReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;

    @NotNull
    @ApiModelProperty("目标成员uid")
    private Long targetUid;
}

package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 标记群公告已读请求
 */
@Data
public class GroupNoticeReadReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;
}

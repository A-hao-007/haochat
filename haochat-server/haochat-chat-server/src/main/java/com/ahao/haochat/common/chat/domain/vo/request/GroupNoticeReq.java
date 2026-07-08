package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 更新群公告请求
 */
@Data
public class GroupNoticeReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;

    @Size(max = 500)
    @ApiModelProperty("群公告内容")
    private String notice;
}

package com.ahao.haochat.common.chat.domain.vo.request.member;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class MemberMuteReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;

    @NotNull
    @ApiModelProperty("被禁言的uid")
    private Long uid;

    @ApiModelProperty("禁言分钟数")
    private Integer minutes;
}

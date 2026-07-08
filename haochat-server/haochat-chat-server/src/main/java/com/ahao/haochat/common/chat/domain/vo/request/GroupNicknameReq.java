package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 更新我在群里的昵称请求
 */
@Data
public class GroupNicknameReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;

    @Size(max = 20)
    @ApiModelProperty("群内昵称")
    private String nickname;
}

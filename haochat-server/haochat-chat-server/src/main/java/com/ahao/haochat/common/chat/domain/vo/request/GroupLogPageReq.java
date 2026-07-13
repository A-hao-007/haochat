package com.ahao.haochat.common.chat.domain.vo.request;

import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class GroupLogPageReq extends CursorPageBaseReq {
    @NotNull
    @ApiModelProperty("房间号")
    private Long roomId;
}

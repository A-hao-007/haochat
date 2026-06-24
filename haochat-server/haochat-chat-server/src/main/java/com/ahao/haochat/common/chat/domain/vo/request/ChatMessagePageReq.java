package com.ahao.haochat.common.chat.domain.vo.request;

import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * Description: 消息列表请求
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessagePageReq extends CursorPageBaseReq {
    @NotNull
    @ApiModelProperty("会话id")
    private Long roomId;
}

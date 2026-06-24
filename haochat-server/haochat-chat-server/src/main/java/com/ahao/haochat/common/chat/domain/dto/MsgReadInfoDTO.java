package com.ahao.haochat.common.chat.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-17
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MsgReadInfoDTO {
    @ApiModelProperty("消息id")
    private Long msgId;

    @ApiModelProperty("已读数")
    private Integer readCount;

    @ApiModelProperty("未读数")
    private Integer unReadCount;

}

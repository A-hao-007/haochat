package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-17
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageReadInfoReq {
    @ApiModelProperty("消息id集合（只查本人）")
    @Size(max = 20)
    private List<Long> msgIds;
}

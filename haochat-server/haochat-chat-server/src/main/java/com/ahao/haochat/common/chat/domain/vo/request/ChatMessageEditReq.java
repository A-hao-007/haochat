package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * [AUDIT-ADD] B-消息编辑：消息编辑请求体（仅文本消息）。
 * 复用 {@link ChatMessageBaseReq} 的字段风格，新增编辑后的内容。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEditReq {
    @NotNull
    @ApiModelProperty("消息id")
    private Long msgId;

    @NotNull
    @ApiModelProperty("会话id")
    private Long roomId;

    @NotNull
    @ApiModelProperty("编辑后的文本内容")
    private String content;
}

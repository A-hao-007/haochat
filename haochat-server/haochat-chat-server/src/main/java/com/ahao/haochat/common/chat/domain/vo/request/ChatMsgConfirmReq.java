package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * AI助手待确认高风险操作：确认/取消
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMsgConfirmReq {
    @NotNull
    @ApiModelProperty("会话id")
    private Long roomId;

    @NotNull
    @ApiModelProperty("是否确认执行，true=确认 false=取消")
    private Boolean confirmed;
}

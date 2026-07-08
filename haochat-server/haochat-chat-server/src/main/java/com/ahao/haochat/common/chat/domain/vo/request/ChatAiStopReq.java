package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * 停止AI流式回复
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatAiStopReq {
    @NotBlank
    @ApiModelProperty("流式回复的streamId（随WS片段下发）")
    private String streamId;
}

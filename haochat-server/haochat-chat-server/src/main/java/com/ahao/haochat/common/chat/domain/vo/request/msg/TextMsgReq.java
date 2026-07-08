package com.ahao.haochat.common.chat.domain.vo.request.msg;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Description: 文本消息入参
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-06-04
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TextMsgReq {

    @NotBlank(message = "内容不能为空")
    @Size(max = 1024, message = "消息内容过长，服务器扛不住啊，兄dei")
    @ApiModelProperty("消息内容")
    private String content;

    @ApiModelProperty("回复的消息id,如果没有别传就好")
    private Long replyMsgId;

    @ApiModelProperty("艾特的uid")
    @Size(max = 10, message = "一次别艾特这么多人")
    private List<Long> atUidList;

    /**
     * 本条消息是否是AI助手发起的、需要用户确认才会真正执行的高风险操作提议。
     * 仅供AI助手内部发消息时使用，{@link com.ahao.haochat.common.chat.service.strategy.msg.TextMsgHandler}
     * 会校验发送者是否为已注册AI助手，非AI助手传入这个字段不会生效——避免普通用户伪造。
     */
    private Boolean needsConfirmation;
}

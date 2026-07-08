package com.ahao.haochat.common.chat.domain.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Description: 消息
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageResp {

    @ApiModelProperty("发送者信息")
    private UserInfo fromUser;
    @ApiModelProperty("消息详情")
    private Message message;

    @Data
    public static class UserInfo {
        @ApiModelProperty("用户id")
        private Long uid;
        @ApiModelProperty("发送者IP")
        private String senderIp;
        @ApiModelProperty("发送者IP属地（解析失败/内网返回'未知'）")
        private String senderLocation;
        @ApiModelProperty("是否为AI助手消息")
        private Boolean isBot;
    }

    @Data
    public static class Message {
        @ApiModelProperty("消息id")
        private Long id;
        @ApiModelProperty("房间id")
        private Long roomId;
        @ApiModelProperty("消息发送时间")
        private Date sendTime;
        @ApiModelProperty("消息类型 1正常文本 2.撤回消息")
        private Integer type;
        @ApiModelProperty("消息内容不同的消息类型，内容体不同，见https://www.yuque.com/snab/mallcaht/rkb2uz5k1qqdmcmd")
        private Object body;
        @ApiModelProperty("消息标记")
        private MessageMark messageMark;
    }

    @Data
    public static class MessageMark {
        @ApiModelProperty("点赞数")
        private Integer likeCount;
        @ApiModelProperty("该用户是否已经点赞 0否 1是")
        private Integer userLike;
        @ApiModelProperty("举报数")
        private Integer dislikeCount;
        @ApiModelProperty("该用户是否已经举报 0否 1是")
        private Integer userDislike;
    }
}

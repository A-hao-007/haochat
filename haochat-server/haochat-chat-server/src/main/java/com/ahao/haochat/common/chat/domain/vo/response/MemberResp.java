package com.ahao.haochat.common.chat.domain.vo.response;

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
public class MemberResp {
    @ApiModelProperty("房间id")
    private Long roomId;
    @ApiModelProperty("群名称")
    private String groupName;
    @ApiModelProperty("群头像")
    private String avatar;
    @ApiModelProperty("在线人数")
    private Long onlineNum;//在线人数
    /**
     * @see com.ahao.haochat.common.chat.domain.enums.GroupRoleAPPEnum
     */
    @ApiModelProperty("成员角色 1群主 2管理员 3普通成员 4踢出群聊")
    private Integer role;
    @ApiModelProperty("群公告")
    private String notice;
    @ApiModelProperty("群公告发布者uid")
    private Long noticeUid;
    @ApiModelProperty("群公告发布时间（毫秒时间戳）")
    private Long noticeTime;
    @ApiModelProperty("群公告已读人数")
    private Integer noticeReadCount;
    @ApiModelProperty("群成员总数（不含已退出）")
    private Integer noticeTotalCount;
    @ApiModelProperty("我是否已读最新公告")
    private Boolean noticeReadByMe;
    @ApiModelProperty("我在群里的昵称")
    private String nickname;
}

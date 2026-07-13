package com.ahao.haochat.common.chat.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GroupOperationTypeEnum {
    CREATE(1, "创建群聊"),
    INVITE(2, "邀请成员"),
    REMOVE(3, "移除成员"),
    EXIT(4, "退出群聊"),
    ADD_ADMIN(5, "设置管理员"),
    REVOKE_ADMIN(6, "取消管理员"),
    TRANSFER_OWNER(7, "转让群主"),
    UPDATE_NOTICE(8, "修改群公告"),
    UPDATE_NICKNAME(9, "修改群昵称"),
    MUTE(10, "群禁言"),
    UNMUTE(11, "取消群禁言"),
    ;

    private final Integer type;
    private final String desc;
}

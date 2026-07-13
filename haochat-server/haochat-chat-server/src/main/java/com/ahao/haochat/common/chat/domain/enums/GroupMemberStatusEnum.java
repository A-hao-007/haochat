package com.ahao.haochat.common.chat.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GroupMemberStatusEnum {
    NORMAL(0, "正常"),
    REMOVED(1, "被移除"),
    EXITED(2, "主动退出"),
    ;

    private final Integer status;
    private final String desc;
}

package com.ahao.haochat.common.user.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author : limeng
 * @description : 申请状态枚举
 * @date : 2023/07/20
 */
@Getter
@AllArgsConstructor
public enum ApplyStatusEnum {

    WAIT_APPROVAL(1, "待审批"),

    AGREE(2, "同意"),

    REJECT(3, "拒绝");

    private final Integer code;

    private final String desc;
}

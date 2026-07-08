package com.ahao.haochat.common.chat.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 群公告信息（存储于 room_group.ext_json，无需新增列）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupNoticeDTO {
    /** 公告内容 */
    private String notice;
    /** 发布者uid */
    private Long noticeUid;
    /** 发布时间（毫秒时间戳） */
    private Long noticeTime;
    /** 已读uid列表 */
    private List<Long> readUidList;
}

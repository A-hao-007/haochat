package com.ahao.haochat.common.chat.dao;

import com.ahao.haochat.common.chat.domain.dto.GroupNoticeDTO;
import com.ahao.haochat.common.chat.domain.entity.RoomGroup;
import com.ahao.haochat.common.chat.mapper.RoomGroupMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 群聊房间表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-22
 */
@Service
public class RoomGroupDao extends ServiceImpl<RoomGroupMapper, RoomGroup> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<RoomGroup> listByRoomIds(List<Long> roomIds) {
        return lambdaQuery()
                .in(RoomGroup::getRoomId, roomIds)
                .list();
    }

    public RoomGroup getByRoomId(Long roomId) {
        return lambdaQuery()
                .eq(RoomGroup::getRoomId, roomId)
                .one();
    }

    public void updateOwner(Long groupId, Long ownerUid) {
        RoomGroup update = new RoomGroup();
        update.setId(groupId);
        update.setOwnerUid(ownerUid);
        updateById(update);
    }

    /**
     * 群公告复用 ext_json 字段存储，无需新增列
     */
    public GroupNoticeDTO getNotice(RoomGroup roomGroup) {
        if (roomGroup == null || StringUtils.isBlank(roomGroup.getExtJson())) {
            return GroupNoticeDTO.builder().readUidList(new ArrayList<>()).build();
        }
        try {
            JsonNode node = MAPPER.readTree(roomGroup.getExtJson());
            List<Long> readUidList = new ArrayList<>();
            node.path("noticeReadUidList").forEach(n -> readUidList.add(n.asLong()));
            return GroupNoticeDTO.builder()
                    .notice(node.path("notice").asText(null))
                    .noticeUid(node.has("noticeUid") && !node.path("noticeUid").isNull() ? node.path("noticeUid").asLong() : null)
                    .noticeTime(node.has("noticeTime") && !node.path("noticeTime").isNull() ? node.path("noticeTime").asLong() : null)
                    .readUidList(readUidList)
                    .build();
        } catch (Exception e) {
            return GroupNoticeDTO.builder().readUidList(new ArrayList<>()).build();
        }
    }

    public void updateNotice(Long groupId, String notice, Long publisherUid) {
        Map<String, Object> ext = new HashMap<>();
        ext.put("notice", notice);
        ext.put("noticeUid", publisherUid);
        ext.put("noticeTime", System.currentTimeMillis());
        ext.put("noticeReadUidList", List.of(publisherUid));
        RoomGroup update = new RoomGroup();
        update.setId(groupId);
        try {
            update.setExtJson(MAPPER.writeValueAsString(ext));
        } catch (Exception e) {
            update.setExtJson(null);
        }
        updateById(update);
    }

    /** 标记当前用户已读最新公告 */
    public void markNoticeRead(RoomGroup roomGroup, Long uid) {
        GroupNoticeDTO current = getNotice(roomGroup);
        if (StringUtils.isBlank(current.getNotice()) || current.getReadUidList().contains(uid)) {
            return;
        }
        current.getReadUidList().add(uid);
        Map<String, Object> ext = new HashMap<>();
        ext.put("notice", current.getNotice());
        ext.put("noticeUid", current.getNoticeUid());
        ext.put("noticeTime", current.getNoticeTime());
        ext.put("noticeReadUidList", current.getReadUidList());
        RoomGroup update = new RoomGroup();
        update.setId(roomGroup.getId());
        try {
            update.setExtJson(MAPPER.writeValueAsString(ext));
        } catch (Exception e) {
            return;
        }
        updateById(update);
    }
}

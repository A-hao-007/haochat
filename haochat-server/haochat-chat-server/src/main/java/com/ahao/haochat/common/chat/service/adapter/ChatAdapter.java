package com.ahao.haochat.common.chat.service.adapter;

import com.ahao.haochat.common.chat.domain.entity.Contact;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.entity.RoomGroup;
import com.ahao.haochat.common.chat.domain.enums.HotFlagEnum;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.common.domain.enums.NormalOrNoEnum;
import com.ahao.haochat.common.user.domain.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-22
 */
public class ChatAdapter {
    public static final String SEPARATOR = ",";

    public static String generateRoomKey(List<Long> uidList) {
        return uidList.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(SEPARATOR));
    }

    public static Room buildRoom(RoomTypeEnum typeEnum) {
        Room room = new Room();
        room.setType(typeEnum.getType());
        room.setHotFlag(HotFlagEnum.NOT.getType());
        return room;
    }

    public static RoomFriend buildFriendRoom(Long roomId, List<Long> uidList) {
        List<Long> collect = uidList.stream().sorted().collect(Collectors.toList());
        RoomFriend roomFriend = new RoomFriend();
        roomFriend.setRoomId(roomId);
        roomFriend.setUid1(collect.get(0));
        roomFriend.setUid2(collect.get(1));
        roomFriend.setRoomKey(generateRoomKey(uidList));
        roomFriend.setStatus(NormalOrNoEnum.NORMAL.getStatus());
        return roomFriend;
    }

    public static Contact buildContact(Long uid, Long roomId) {
        Contact contact = new Contact();
        contact.setRoomId(roomId);
        contact.setUid(uid);
        return contact;
    }

    public static Set<Long> getFriendUidSet(Collection<RoomFriend> values, Long uid) {
        return values.stream()
                .map(a -> getFriendUid(a, uid))
                .collect(Collectors.toSet());
    }

    /**
     * 获取好友uid
     */
    public static Long getFriendUid(RoomFriend roomFriend, Long uid) {
        return Objects.equals(uid, roomFriend.getUid1()) ? roomFriend.getUid2() : roomFriend.getUid1();
    }

    public static RoomGroup buildGroupRoom(User user, Long roomId, String name) {
        RoomGroup roomGroup = new RoomGroup();
        roomGroup.setName(org.apache.commons.lang3.StringUtils.isNotBlank(name) ? name : user.getName() + "的群组");
        // avatar 字段为 NOT NULL，创建者未设置头像时落空字符串（前端对空头像有默认占位展示）
        roomGroup.setAvatar(org.apache.commons.lang3.StringUtils.defaultString(user.getAvatar()));
        roomGroup.setRoomId(roomId);
        return roomGroup;
    }
}

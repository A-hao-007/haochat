package com.ahao.haochat.common.chat.dao;

import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.mapper.RoomFriendMapper;
import com.ahao.haochat.common.common.domain.enums.NormalOrNoEnum;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 单聊房间表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-22
 */
@Service
public class RoomFriendDao extends ServiceImpl<RoomFriendMapper, RoomFriend> {

    public RoomFriend getByKey(String key) {
        return lambdaQuery().eq(RoomFriend::getRoomKey, key).one();
    }

    public void restoreRoom(Long id) {
        lambdaUpdate()
                .eq(RoomFriend::getId, id)
                .set(RoomFriend::getStatus, NormalOrNoEnum.NORMAL.getStatus())
                .update();
    }

    public void disableRoom(String key) {
        lambdaUpdate()
                .eq(RoomFriend::getRoomKey, key)
                .set(RoomFriend::getStatus, NormalOrNoEnum.NOT_NORMAL.getStatus())
                .update();
    }

    public List<RoomFriend> listByRoomIds(List<Long> roomIds) {
        return lambdaQuery()
                .in(RoomFriend::getRoomId, roomIds)
                .list();
    }

    public RoomFriend getByRoomId(Long roomID) {
        return lambdaQuery()
                .eq(RoomFriend::getRoomId, roomID)
                .one();
    }
}

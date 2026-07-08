package com.ahao.haochat.common.chat.dao;

import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.mapper.RoomMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * <p>
 * 房间表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
@Service
public class RoomDao extends ServiceImpl<RoomMapper, Room> implements IService<Room> {

    public void refreshActiveTime(Long roomId, Long msgId, Date msgTime) {
        // 只允许时间前进：MQ 并发消费下同房间两条消息可能乱序处理，
        // 无条件覆盖会让旧消息回退 last_msg_id/active_time（积压重放时尤其明显）
        lambdaUpdate()
                .eq(Room::getId, roomId)
                .and(w -> w.isNull(Room::getActiveTime).or().lt(Room::getActiveTime, msgTime))
                .set(Room::getLastMsgId, msgId)
                .set(Room::getActiveTime, msgTime)
                .update();
    }
}

package com.ahao.haochat.common.chat.service;

import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话刷新合并缓冲：群聊写扩散的去抖层。
 *
 * 问题：每条群消息都要对全体成员的 contact 行做 upsert（N 人群 = N 行写放大）。
 * 活跃大群每秒多条消息时，contact 写入量 = 消息数 × 成员数，MySQL 首先扛不住。
 *
 * 方案：同房间在合并窗口内的多条消息只触发一次全员刷新。合并是安全的——
 * contact 刷新的 SQL 用 GREATEST 保证 last_msg_id/active_time 只前进不后退，
 * 取窗口内最大的 msgId 刷一次，结果与逐条刷新完全一致（幂等 + 单调）。
 *
 * 代价（面试时主动讲）：
 * 1. 会话列表的"最新消息"最多延迟一个窗口期（1s，用户无感知）；
 * 2. 进程崩溃丢失窗口内未刷新的合并记录——下一条消息会自愈，且消息本体
 *    早已落库（本表只是排序索引），属于可接受的弱一致；
 * 3. 仅适用于单实例。多实例需要改为按 roomId 分片路由或落到 Redis 合并。
 */
@Slf4j
@Component
public class ContactRefreshBuffer {

    /** 合并窗口（毫秒）。窗口越大合并率越高、会话列表延迟越大 */
    private static final long FLUSH_INTERVAL_MS = 1000;

    private final ConcurrentHashMap<Long, Pending> pendingByRoom = new ConcurrentHashMap<>();

    @Autowired
    private RoomCache roomCache;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private ContactDao contactDao;

    private static class Pending {
        long msgId;
        Date activeTime;
    }

    /**
     * 提交一次会话刷新请求（合并到所在房间的待刷新记录，只保留最大 msgId）
     */
    public void submit(Long roomId, Long msgId, Date activeTime) {
        pendingByRoom.compute(roomId, (k, v) -> {
            if (v == null) {
                v = new Pending();
            }
            if (msgId > v.msgId) {
                v.msgId = msgId;
                v.activeTime = activeTime;
            }
            return v;
        });
    }

    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    public void flush() {
        if (pendingByRoom.isEmpty()) {
            return;
        }
        for (Long roomId : pendingByRoom.keySet()) {
            Pending p = pendingByRoom.remove(roomId);
            if (p == null) {
                continue;
            }
            try {
                doRefresh(roomId, p);
            } catch (Exception e) {
                log.error("contact 合并刷新失败 roomId={} msgId={}，回投下一窗口重试", roomId, p.msgId, e);
                //回投重试：GREATEST 语义下重复刷新无副作用
                submit(roomId, p.msgId, p.activeTime);
            }
        }
    }

    private void doRefresh(Long roomId, Pending p) {
        Room room = roomCache.get(roomId);
        if (room == null || room.isHotRoom()) {
            //热门房间走读扩散（HotRoomCache），不写成员会话
            return;
        }
        List<Long> memberUidList = null;
        if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
            memberUidList = groupMemberCache.getMemberUidList(roomId);
        } else if (Objects.equals(room.getType(), RoomTypeEnum.FRIEND.getType())) {
            RoomFriend roomFriend = roomFriendDao.getByRoomId(roomId);
            if (roomFriend != null) {
                memberUidList = Arrays.asList(roomFriend.getUid1(), roomFriend.getUid2());
            }
        }
        if (memberUidList == null || memberUidList.isEmpty()) {
            return;
        }
        contactDao.refreshOrCreateActiveTime(roomId, memberUidList, p.msgId, p.activeTime);
    }
}

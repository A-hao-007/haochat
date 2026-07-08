-- 背景1（回填）：RocketMQ broker 的 StoreUtil bug 修复后，积压的 1160 条消息被乱序回放
-- （同房间的消息分散在4个队列，消费顺序不等于消息时间顺序），而 room.refreshActiveTime /
-- contact.refreshOrCreateActiveTime 都是"后写覆盖"，导致部分房间的 last_msg_id/active_time
-- 被写成了非最新消息的值，会话列表排序错乱。用 message 表的真实数据一次性修正。
UPDATE room r
SET r.last_msg_id = (SELECT MAX(m.id) FROM message m WHERE m.room_id = r.id),
    r.active_time = (SELECT MAX(m.create_time) FROM message m WHERE m.room_id = r.id)
WHERE EXISTS (SELECT 1 FROM message m WHERE m.room_id = r.id);

UPDATE contact c
SET c.last_msg_id = (SELECT MAX(m.id) FROM message m WHERE m.room_id = c.room_id),
    c.active_time = (SELECT MAX(m.create_time) FROM message m WHERE m.room_id = c.room_id)
WHERE EXISTS (SELECT 1 FROM message m WHERE m.room_id = c.room_id);

-- 背景2（下线全员群）：产品决定移除"好聊全员群"（room_id=1, hot_flag=1）。
-- 热门房间通过 Redis zset(haochat:hotRoom) 注入所有人的会话列表，不依赖 contact/group_member。
-- 关掉 hot_flag 后它退化为普通群，而它的 group_member 为空、再删掉遗留的 2 行 contact，
-- 就不会出现在任何人的会话列表里。消息历史保留在 message 表，不做破坏性删除。
-- （Redis 侧需要配合执行：DEL haochat:hotRoom，代码里只有 isHotRoom 的房间会往 zset 里写，
--  hot_flag=0 之后不会复活。）
UPDATE room SET hot_flag = 0 WHERE id = 1;
DELETE FROM contact WHERE room_id = 1;

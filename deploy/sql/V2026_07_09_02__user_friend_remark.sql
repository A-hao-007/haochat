-- 补齐 user_friend.remark 列（好友备注）。
-- 实体 UserFriend 自初始版本即有 remark 字段，但基础 schema/历史迁移从未建列，
-- 新环境从 haochat.sql 初始化后调用好友接口会报 Unknown column 'remark'。
-- 服务器生产库若已手工加过此列，执行前先 SHOW COLUMNS 确认。
ALTER TABLE `user_friend`
    ADD COLUMN `remark` VARCHAR(64) NULL COMMENT '好友备注' AFTER `friend_uid`;

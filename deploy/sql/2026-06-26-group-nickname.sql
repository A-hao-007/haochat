-- HaoChat 群成员昵称字段迁移
-- 日期: 2026-06-26
-- 说明: 为 group_member 表增加 nickname（群内昵称），群公告复用已有的 room_group.ext_json，无需迁移

ALTER TABLE `group_member`
    ADD COLUMN `nickname` VARCHAR(64) NULL COMMENT '群内昵称' AFTER `role`;

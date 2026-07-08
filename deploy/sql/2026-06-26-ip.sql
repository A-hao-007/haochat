-- HaoChat IP 字段迁移
-- 日期: 2026-06-26
-- 说明: 为 message 表增加 sender_ip，为 user 表增加 last_login_ip

ALTER TABLE `message`
    ADD COLUMN `sender_ip` VARCHAR(45) NULL COMMENT '发送者IP地址' AFTER `from_uid`;

ALTER TABLE `user`
    ADD COLUMN `last_login_ip` VARCHAR(45) NULL COMMENT '最近登录IP地址' AFTER `ip_info`;

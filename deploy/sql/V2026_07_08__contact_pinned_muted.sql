-- 补齐 contact 表的会话置顶/免打扰列（服务器已有、迁移脚本缺失）。
-- 服务器生产库执行前先 SHOW COLUMNS 确认不存在。
ALTER TABLE `contact`
    ADD COLUMN `pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '会话置顶 0否 1是' AFTER `last_msg_id`,
    ADD COLUMN `muted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '消息免打扰 0否 1是' AFTER `pinned`;

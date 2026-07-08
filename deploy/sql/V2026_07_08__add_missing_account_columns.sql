-- 补齐服务器上已存在但迁移脚本缺失的三列（本地/新环境从 haochat.sql 初始化后必须执行）。
-- 服务器生产库已有这些列，请勿在生产重复执行（或先 SHOW COLUMNS 确认）。
ALTER TABLE `user`
    ADD COLUMN `username` VARCHAR(64) NULL COMMENT '用户名（登录用，唯一）' AFTER `name`,
    ADD COLUMN `password` VARCHAR(100) NULL COMMENT '密码（BCrypt加密）' AFTER `email`,
    ADD COLUMN `status_msg` VARCHAR(255) NULL COMMENT '个性签名/状态' AFTER `status`;

CREATE UNIQUE INDEX `uniq_username` ON `user` (`username`);

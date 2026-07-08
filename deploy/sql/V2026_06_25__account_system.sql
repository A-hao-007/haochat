-- 账号体系升级（忘记密码 / 找回密码）所需的库表变更
-- 在生产库执行前请先备份。安全可重复执行（IF NOT EXISTS 由人工确认，MySQL 8 ADD COLUMN 不支持 IF NOT EXISTS 于所有版本，故执行前确认列不存在）。

-- 1) 用户表新增邮箱列（找回密码用）
ALTER TABLE `user`
    ADD COLUMN `email` VARCHAR(128) NULL COMMENT '邮箱（找回密码用）' AFTER `username`;

-- 可选：为邮箱建立唯一索引（同一邮箱只绑定一个账号）。若历史数据可能有重复 NULL，唯一索引允许多个 NULL，安全。
-- CREATE UNIQUE INDEX `uniq_user_email` ON `user` (`email`);

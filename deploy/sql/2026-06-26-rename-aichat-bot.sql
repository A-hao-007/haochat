-- 将系统内置AI助手账号（uid=10001）的显示名称由 ChatGPT 改为 Aichat
-- 日期: 2026-06-26
UPDATE `user` SET `name` = 'Aichat' WHERE `id` = 10001 AND `name` = 'ChatGPT';

ALTER TABLE `room_group`
  ADD COLUMN `owner_uid` BIGINT DEFAULT NULL COMMENT '群主uid' AFTER `avatar`;

UPDATE `room_group` rg
JOIN `group_member` gm ON gm.`group_id` = rg.`id` AND gm.`role` = 1
SET rg.`owner_uid` = gm.`uid`
WHERE rg.`owner_uid` IS NULL;

ALTER TABLE `group_member`
  ADD COLUMN `status` INT NOT NULL DEFAULT 0 COMMENT '成员状态 0正常 1被移除 2主动退出' AFTER `role`,
  ADD COLUMN `mute_end_time` DATETIME(3) DEFAULT NULL COMMENT '禁言截止时间' AFTER `status`;

CREATE INDEX `idx_group_member_status` ON `group_member` (`group_id`, `status`);

CREATE TABLE IF NOT EXISTS `group_operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `room_id` BIGINT NOT NULL COMMENT '房间id',
  `group_id` BIGINT NOT NULL COMMENT '群组id',
  `operator_uid` BIGINT NOT NULL COMMENT '操作人uid',
  `target_uid` BIGINT DEFAULT NULL COMMENT '目标用户uid',
  `type` INT NOT NULL COMMENT '操作类型',
  `content` VARCHAR(255) NOT NULL COMMENT '操作内容',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_room_id_id` (`room_id`, `id`),
  KEY `idx_group_id_id` (`group_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群操作日志';

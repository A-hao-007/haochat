CREATE TABLE IF NOT EXISTS `file_asset` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uid` BIGINT NOT NULL, `room_id` BIGINT NULL,
  `scene` VARCHAR(32) NOT NULL, `object_key` VARCHAR(512) NOT NULL,
  `original_name` VARCHAR(255) NOT NULL, `content_type` VARCHAR(128) NOT NULL,
  `size` BIGINT NOT NULL, `status` TINYINT NOT NULL DEFAULT 0,
  `thumbnail_key` VARCHAR(512) NULL, `virus_status` TINYINT NOT NULL DEFAULT 0,
  `expire_time` DATETIME(3) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_object_key` (`object_key`),
  KEY `idx_uid_status` (`uid`,`status`), KEY `idx_room_status` (`room_id`,`status`), KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `file_process_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, `asset_id` BIGINT UNSIGNED NOT NULL,
  `task_type` VARCHAR(32) NOT NULL, `status` TINYINT NOT NULL DEFAULT 0, `retry_count` INT NOT NULL DEFAULT 0,
  `error_msg` VARCHAR(512) NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_asset_task` (`asset_id`,`task_type`), KEY `idx_status` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

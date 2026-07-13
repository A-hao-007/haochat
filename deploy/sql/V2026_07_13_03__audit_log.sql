CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `aggregate_id` VARCHAR(128) NULL,
  `operator_uid` BIGINT NULL,
  `payload` JSON NULL,
  `occurred_at` DATETIME(3) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_event_id` (`event_id`),
  KEY `idx_audit_type_time` (`event_type`, `create_time`),
  KEY `idx_audit_operator_time` (`operator_uid`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步业务审计日志';

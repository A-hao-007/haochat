CREATE TABLE IF NOT EXISTS `user_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Session ID',
  `uid` BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
  `refresh_token_hash` VARCHAR(64) NULL COMMENT 'SHA-256 hash of refresh token',
  `device_id` VARCHAR(64) NULL COMMENT 'Derived device identifier',
  `device_name` VARCHAR(64) NULL COMMENT 'Device name',
  `user_agent` VARCHAR(512) NULL COMMENT 'User-Agent',
  `ip` VARCHAR(45) NULL COMMENT 'Login IP',
  `ip_region` VARCHAR(128) NULL COMMENT 'IP location',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 revoked',
  `expire_time` DATETIME(3) NOT NULL COMMENT 'Refresh token expiry',
  `last_active_time` DATETIME(3) NOT NULL COMMENT 'Last refresh/login time',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_user_session_uid_status` (`uid`, `status`),
  KEY `idx_user_session_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User login sessions';

CREATE TABLE IF NOT EXISTS `user_login_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Log ID',
  `uid` BIGINT UNSIGNED NULL COMMENT 'User ID, null when principal is unknown',
  `principal` VARCHAR(128) NULL COMMENT 'Submitted username/email',
  `login_type` VARCHAR(32) NOT NULL COMMENT 'PASSWORD/EMAIL_PASSWORD/EMAIL_CODE/etc',
  `success` TINYINT NOT NULL COMMENT '0 failed, 1 success',
  `fail_reason` VARCHAR(128) NULL COMMENT 'Failure reason code',
  `ip` VARCHAR(45) NULL COMMENT 'Client IP',
  `ip_region` VARCHAR(128) NULL COMMENT 'IP location',
  `device_id` VARCHAR(64) NULL COMMENT 'Derived device identifier',
  `device_name` VARCHAR(64) NULL COMMENT 'Device name',
  `user_agent` VARCHAR(512) NULL COMMENT 'User-Agent',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_login_log_uid_time` (`uid`, `create_time`),
  KEY `idx_login_log_principal_time` (`principal`, `create_time`),
  KEY `idx_login_log_ip_time` (`ip`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User login audit log';

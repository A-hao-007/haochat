ALTER TABLE `ai_memory`
    ADD COLUMN `entities_json` json DEFAULT NULL COMMENT 'entities extracted from source message' AFTER `status`,
    ADD COLUMN `sync_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SYNCING, READY, FAILED' AFTER `entities_json`,
    ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 AFTER `sync_status`,
    ADD COLUMN `last_error` varchar(500) DEFAULT NULL AFTER `retry_count`,
    ADD COLUMN `next_retry_time` datetime(3) DEFAULT NULL AFTER `last_error`,
    ADD COLUMN `synced_time` datetime(3) DEFAULT NULL AFTER `next_retry_time`,
    ADD KEY `idx_sync_retry` (`sync_status`, `next_retry_time`);

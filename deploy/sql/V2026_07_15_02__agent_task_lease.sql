-- Recoverable Agent tasks: lease + fencing token + bounded exponential retry.
ALTER TABLE `agent_task`
    ADD COLUMN `lease_until` DATETIME(3) NULL AFTER `max_run_count`,
    ADD COLUMN `lease_token` VARCHAR(36) NULL AFTER `lease_until`,
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 AFTER `lease_token`,
    ADD COLUMN `last_error` VARCHAR(1000) NULL AFTER `retry_count`,
    ADD KEY `idx_agent_task_lease` (`status`, `lease_until`);

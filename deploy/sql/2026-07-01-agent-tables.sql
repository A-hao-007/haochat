-- HaoChat AI Agent 升级：任务状态机 + 调用日志
-- 日期: 2026-07-01
-- 说明: agent_task 取代原先写 Redis ZSet 的定时提醒方式，支持扩展更多任务类型；
--       ai_call_log 记录每次 Agent 调用的输入/输出/工具序列/耗时，用于可观测性与后续评测集积累。

CREATE TABLE IF NOT EXISTS `agent_task` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
    `uid`             BIGINT          NOT NULL COMMENT '创建者',
    `room_id`         BIGINT          NOT NULL COMMENT '投递目标会话',
    `task_type`       VARCHAR(32)     NOT NULL COMMENT '任务类型，如 REMINDER',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2等待事件 3已完成 4已取消 5失败',
    `payload`         JSON            NULL COMMENT '任务参数，按task_type解释',
    `next_run_time`   DATETIME(3)     NOT NULL COMMENT '下次该被调度的时间',
    `last_run_time`   DATETIME(3)     NULL COMMENT '上次执行时间',
    `run_count`       INT             NOT NULL DEFAULT 0 COMMENT '已执行次数',
    `max_run_count`   INT             NULL COMMENT '周期任务的执行次数上限，防止永久空转',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_next_run_time` (`status`, `next_run_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 任务状态机';

CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
    `uid`                 BIGINT          NOT NULL COMMENT '发起用户',
    `room_id`             BIGINT          NOT NULL COMMENT '所在会话',
    `user_input`          VARCHAR(2048)   NULL COMMENT '用户原始输入',
    `final_reply`         TEXT            NULL COMMENT 'AI最终回复',
    `tool_calls`          JSON            NULL COMMENT '本次调用涉及的工具序列',
    `turn_count`          INT             NOT NULL DEFAULT 0 COMMENT '模型往返轮数',
    `latency_ms`          INT             NULL COMMENT '总耗时(毫秒)',
    `success`             TINYINT         NOT NULL DEFAULT 1 COMMENT '0失败 1成功',
    `error_msg`           VARCHAR(512)    NULL COMMENT '失败原因',
    `needs_confirmation`  TINYINT         NOT NULL DEFAULT 0 COMMENT '本次是否触发了需要用户确认的动作',
    `confirmed`           TINYINT         NULL COMMENT '用户是否确认执行(仅needs_confirmation=1时有意义)',
    `create_time`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_uid_create_time` (`uid`, `create_time`),
    KEY `idx_room_id_create_time` (`room_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI Agent 调用日志';

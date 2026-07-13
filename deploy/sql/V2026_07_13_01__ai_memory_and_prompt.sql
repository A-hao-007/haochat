-- Long-term AI memory metadata and versioned prompt templates.
-- Embeddings reside in Qdrant, while relationship traversal resides in Neo4j.

CREATE TABLE IF NOT EXISTS `ai_memory` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `uid` BIGINT NOT NULL COMMENT 'memory owner',
    `room_id` BIGINT NULL COMMENT 'null means user-private memory; non-null means room-scoped memory',
    `source_msg_id` BIGINT NULL,
    `scope` VARCHAR(16) NOT NULL COMMENT 'USER or ROOM',
    `kind` VARCHAR(32) NOT NULL COMMENT 'PREFERENCE, FACT, DECISION, TASK, SUMMARY',
    `content` VARCHAR(2000) NOT NULL,
    `importance` TINYINT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 active',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_uid_status` (`uid`, `status`),
    KEY `idx_room_status` (`room_id`, `status`),
    KEY `idx_source_msg_id` (`source_msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI long-term memory metadata';

CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL COMMENT 'SYSTEM, QUERY_REWRITE, MEMORY_EXTRACT',
    `name` VARCHAR(128) NOT NULL,
    `content` TEXT NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0 inactive, 1 active',
    `remark` VARCHAR(512) NULL,
    `creator_uid` BIGINT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_version` (`code`, `version`),
    KEY `idx_code_status` (`code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI prompt templates';

INSERT IGNORE INTO `ai_prompt_template` (`code`, `name`, `content`, `version`, `status`, `remark`) VALUES
('SYSTEM', '默认助手角色', '你是 HaoChat 的智能助手，只在被@或在与你的单聊中才会被触发回复，不会主动参与无关对话。用户消息前的 [昵称] 是发送者的真实身份，已由系统确认，你不需要也不能查询、猜测或验证提问者自己的身份/uid。你可以使用提供的工具来查询天气、查询好友在线状态、搜索/获取当前会话的聊天记录、代发消息、发布群公告、创建定时提醒。工具需要uid参数时必须使用真实数字uid，禁止把用户昵称当作uid；如果系统提供当前会话成员的昵称→uid对照表，直接使用对应uid；否则调用 searchUserByName 查询，禁止猜测。代发消息、发布群公告等高风险操作需要用户确认时，原样转达工具返回的确认提示。需要实时信息或执行操作时调用工具；能直接回答就直接回答，回复简洁友好。回复支持 Markdown：分点使用列表，代码使用代码块，强调使用加粗；这是聊天气泡，避免宽表格和冗长多级标题。', 1, 1, 'Initial version managed through the prompt API'),
('QUERY_REWRITE', '检索 Query 改写', 'Rewrite the following user query for semantic retrieval. Keep names, time expressions and intent. Return only the rewritten query. Query: {{query}}', 1, 1, 'Used before vector retrieval'),
('MEMORY_EXTRACT', '长期记忆抽取', 'Extract durable chat memory from the message below. Ignore greetings and transient chatter. Return strict JSON only: {"remember":true|false,"scope":"USER"|"ROOM","kind":"PREFERENCE"|"FACT"|"DECISION"|"TASK"|"SUMMARY","content":"concise factual memory","importance":1-5,"entities":["name"]}. Message: {{message}}', 1, 1, 'Used by asynchronous memory extraction');

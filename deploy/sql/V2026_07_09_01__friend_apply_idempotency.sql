ALTER TABLE `user_apply`
  ADD COLUMN `pending_target_id` BIGINT GENERATED ALWAYS AS (
    CASE WHEN `status` = 1 THEN `target_id` ELSE NULL END
  ) STORED COMMENT 'Pending target id for unique pending friend apply';

CREATE UNIQUE INDEX `uniq_user_apply_pending_friend`
  ON `user_apply` (`uid`, `type`, `pending_target_id`);

ALTER TABLE `user_friend`
  ADD COLUMN `active_friend_uid` BIGINT GENERATED ALWAYS AS (
    CASE WHEN `delete_status` = 0 THEN `friend_uid` ELSE NULL END
  ) STORED COMMENT 'Active friend uid for unique active friend relation';

CREATE UNIQUE INDEX `uniq_user_friend_active`
  ON `user_friend` (`uid`, `active_friend_uid`);

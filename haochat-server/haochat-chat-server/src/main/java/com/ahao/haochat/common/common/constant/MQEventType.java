package com.ahao.haochat.common.common.constant;

/** Stable event names. Keep these values backward compatible once published. */
public interface MQEventType {
    String NEW_MESSAGE = "message.created";
    String FRIEND_APPLY = "friend.apply.created";
    String FRIEND_APPLY_ACCEPTED = "friend.apply.accepted";
    String GROUP_MEMBER_JOINED = "group.member.joined";
    String GROUP_MEMBER_LEFT = "group.member.left";
    String AI_REQUESTED = "ai.requested";
    String AI_REPLY_COMPLETED = "ai.reply.completed";
    String FILE_UPLOAD_COMPLETED = "file.upload.completed";
    String MESSAGE_REPORTED = "message.reported";
    String REMINDER_DUE = "reminder.due";
    String MESSAGE_RECALLED = "message.recalled";
    String MESSAGE_EDITED = "message.edited";
    String MESSAGE_MARKED = "message.marked";
    String USER_REGISTERED = "user.registered";
    String USER_ONLINE = "user.online";
    String USER_OFFLINE = "user.offline";
    String USER_BLACKLISTED = "user.blacklisted";
    String ITEM_RECEIVED = "item.received";
}

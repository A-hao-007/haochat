package com.ahao.haochat.common.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GroupMemberLeftEvent extends ApplicationEvent {
    private final Long groupId;
    private final Long roomId;
    private final Long operatorUid;
    private final Long memberUid;

    public GroupMemberLeftEvent(Object source, Long groupId, Long roomId, Long operatorUid, Long memberUid) {
        super(source);
        this.groupId = groupId;
        this.roomId = roomId;
        this.operatorUid = operatorUid;
        this.memberUid = memberUid;
    }
}

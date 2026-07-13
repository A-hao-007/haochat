package com.ahao.haochat.common.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FriendApplyAcceptedEvent extends ApplicationEvent {
    private final Long applyId;
    private final Long uid;
    private final Long friendUid;
    private final Long roomId;

    public FriendApplyAcceptedEvent(Object source, Long applyId, Long uid, Long friendUid, Long roomId) {
        super(source);
        this.applyId = applyId;
        this.uid = uid;
        this.friendUid = friendUid;
        this.roomId = roomId;
    }
}

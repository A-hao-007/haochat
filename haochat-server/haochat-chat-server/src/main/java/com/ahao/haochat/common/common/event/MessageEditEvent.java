package com.ahao.haochat.common.common.event;

import org.springframework.context.ApplicationEvent;

public class MessageEditEvent extends ApplicationEvent {
    private final Long msgId;

    public MessageEditEvent(Object source, Long msgId) {
        super(source);
        this.msgId = msgId;
    }

    public Long getMsgId() {
        return msgId;
    }
}

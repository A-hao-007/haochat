package com.ahao.haochat.common.common.event;

import com.ahao.haochat.common.user.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserOnlineEvent extends ApplicationEvent {
    private final User user;

    public UserOnlineEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}

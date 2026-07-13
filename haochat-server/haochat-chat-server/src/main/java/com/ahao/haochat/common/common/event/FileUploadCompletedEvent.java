package com.ahao.haochat.common.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileUploadCompletedEvent extends ApplicationEvent {
    private final Long assetId;

    public FileUploadCompletedEvent(Object source, Long assetId) {
        super(source);
        this.assetId = assetId;
    }
}

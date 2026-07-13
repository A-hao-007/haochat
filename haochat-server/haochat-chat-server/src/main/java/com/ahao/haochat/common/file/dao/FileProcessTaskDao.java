package com.ahao.haochat.common.file.dao;

import com.ahao.haochat.common.file.domain.FileProcessTask;
import com.ahao.haochat.common.file.mapper.FileProcessTaskMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class FileProcessTaskDao extends ServiceImpl<FileProcessTaskMapper, FileProcessTask> {
    public static final int PENDING = 0;
    public static final int RUNNING = 1;
    public static final int SUCCESS = 2;
    public static final int FAILED = 3;
    public static final int SKIPPED = 4;

    public static final String THUMBNAIL = "THUMBNAIL";
    public static final String VIRUS_SCAN = "VIRUS_SCAN";

    public void enqueue(Long assetId, String taskType) {
        FileProcessTask task = new FileProcessTask();
        task.setAssetId(assetId);
        task.setTaskType(taskType);
        task.setStatus(PENDING);
        task.setRetryCount(0);
        try {
            save(task);
        } catch (DuplicateKeyException ignored) {
            // Duplicate submit/confirm should not create duplicate background work.
        }
    }

    public List<FileProcessTask> listPending(int limit) {
        return lambdaQuery()
                .eq(FileProcessTask::getStatus, PENDING)
                .orderByAsc(FileProcessTask::getId)
                .last("limit " + limit)
                .list();
    }

    public boolean claim(Long taskId) {
        return lambdaUpdate()
                .eq(FileProcessTask::getId, taskId)
                .eq(FileProcessTask::getStatus, PENDING)
                .set(FileProcessTask::getStatus, RUNNING)
                .set(FileProcessTask::getUpdateTime, new Date())
                .update();
    }
}

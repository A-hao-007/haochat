package com.ahao.haochat.common.file.consumer;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.service.IChatAIService;
import com.ahao.haochat.common.chatai.agent.task.AgentTask;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskDao;
import com.ahao.haochat.common.chatai.agent.task.TaskExecutor;
import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.constant.MQEventType;
import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.domain.dto.MQBusinessEvent;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.ahao.haochat.utils.JsonUtils;
import com.ahao.haochat.common.file.dao.FileAssetDao;
import com.ahao.haochat.common.file.dao.FileProcessTaskDao;
import com.ahao.haochat.common.file.domain.FileAsset;
import com.ahao.haochat.common.file.service.FileAssetService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Handles MQ work that is safe to retry and has idempotent database writes. */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(consumerGroup = MQConstant.BUSINESS_PROCESSOR_GROUP, topic = MQConstant.BUSINESS_EVENT_TOPIC,
        consumeThreadNumber = 2, consumeThreadMax = 4)
public class BusinessEventProcessorConsumer implements RocketMQListener<MQBusinessEvent> {
    private final MessageDao messageDao;
    private final IChatAIService chatAIService;
    private final FileAssetDao fileAssetDao;
    private final FileProcessTaskDao fileProcessTaskDao;
    private final AgentTaskDao agentTaskDao;
    private final List<TaskExecutor> taskExecutors;

    @Override
    public void onMessage(MQBusinessEvent event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            return;
        }
        Boolean firstDelivery = RedisUtils.setIfAbsent(
                RedisKey.getKey("mq:processor:processed:%s", event.getEventId()), "processing", 30,
                java.util.concurrent.TimeUnit.MINUTES);
        if (firstDelivery == null) {
            throw new IllegalStateException("Redis unavailable while claiming MQ event");
        }
        if (!firstDelivery) {
            return;
        }
        try {
            if (MQEventType.NEW_MESSAGE.equals(event.getEventType())) {
                processAI(event);
            } else if (MQEventType.FILE_UPLOAD_COMPLETED.equals(event.getEventType())) {
                processFile(event);
            } else if (MQEventType.REMINDER_DUE.equals(event.getEventType())) {
                processReminder(event);
            }
            RedisUtils.set(RedisKey.getKey("mq:processor:processed:%s", event.getEventId()), "1", 30,
                    java.util.concurrent.TimeUnit.DAYS);
        } catch (RuntimeException e) {
            RedisUtils.del(RedisKey.getKey("mq:processor:processed:%s", event.getEventId()));
            throw e;
        }
    }

    private void processAI(MQBusinessEvent event) {
        JsonNode payload = JsonUtils.toJsonNode(event.getPayload());
        Long msgId = payload.path("msgId").isNumber() ? payload.path("msgId").longValue() : null;
        if (msgId == null) {
            return;
        }
        Message message = messageDao.getById(msgId);
        if (message != null) {
            chatAIService.chat(message);
        }
    }

    private void processFile(MQBusinessEvent event) {
        JsonNode payload = JsonUtils.toJsonNode(event.getPayload());
        Long assetId = payload.path("assetId").isNumber() ? payload.path("assetId").longValue() : null;
        if (assetId == null) {
            return;
        }
        FileAsset asset = fileAssetDao.getById(assetId);
        if (asset == null || !Integer.valueOf(FileAssetService.AVAILABLE).equals(asset.getStatus())) {
            log.debug("skip file processing for unavailable assetId={}", assetId);
            return;
        }
        fileProcessTaskDao.enqueue(assetId, FileProcessTaskDao.VIRUS_SCAN);
        if (asset.getContentType() != null && asset.getContentType().startsWith("image/")) {
            fileProcessTaskDao.enqueue(assetId, FileProcessTaskDao.THUMBNAIL);
        }
    }

    private void processReminder(MQBusinessEvent event) {
        JsonNode payload = JsonUtils.toJsonNode(event.getPayload());
        Long taskId = payload.path("taskId").isNumber() ? payload.path("taskId").longValue() : null;
        if (taskId == null) {
            return;
        }
        AgentTask task = agentTaskDao.getById(taskId);
        if (task == null || !agentTaskDao.claimDue(taskId, new Date())) {
            return;
        }
        Map<String, TaskExecutor> executorMap = taskExecutors.stream()
                .collect(Collectors.toMap(TaskExecutor::taskType, Function.identity(), (left, right) -> left));
        TaskExecutor executor = executorMap.get(task.getTaskType());
        if (executor == null) {
            agentTaskDao.markFailed(taskId);
            return;
        }
        try {
            executor.execute(task);
        } catch (Exception e) {
            agentTaskDao.markFailed(taskId);
            throw e;
        }
    }
}

package com.ahao.haochat.common.chatai.memory;

import com.ahao.haochat.common.chatai.dao.AiMemoryDao;
import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
import com.ahao.haochat.common.chatai.service.AiModelGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Keeps MySQL, Qdrant and Neo4j in sync and retries partial failures idempotently. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMemorySyncService {
    public static final String PENDING = "PENDING";
    public static final String SYNCING = "SYNCING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final AiMemoryProperties properties;
    private final AiMemoryDao memoryDao;
    private final AiModelGateway modelGateway;
    private final QdrantMemoryClient qdrantMemoryClient;
    private final Neo4jMemoryGraphClient graphClient;

    public void syncNow(Long memoryId) {
        if (!properties.isEnabled() || memoryId == null || !claim(memoryId)) {
            return;
        }
        AiMemory memory = memoryDao.getById(memoryId);
        if (memory == null) {
            return;
        }
        try {
            String input = properties.getEmbeddingDocumentPrefix() + memory.getContent();
            List<Float> vector = modelGateway.embed(input, properties.getEmbeddingModel());
            qdrantMemoryClient.upsert(memory, vector);
            graphClient.upsert(memory, entities(memory.getEntitiesJson()));
            Date now = new Date();
            memoryDao.lambdaUpdate()
                    .eq(AiMemory::getId, memoryId)
                    .set(AiMemory::getSyncStatus, READY)
                    .set(AiMemory::getLastError, null)
                    .set(AiMemory::getNextRetryTime, null)
                    .set(AiMemory::getSyncedTime, now)
                    .update();
            log.info("AI memory sync completed memoryId={}", memoryId);
        } catch (Exception e) {
            int retry = (memory.getRetryCount() == null ? 0 : memory.getRetryCount()) + 1;
            long baseDelay = Math.max(10, properties.getSyncRetryDelaySeconds());
            long delay = Math.min(3600, baseDelay * (1L << Math.min(6, retry - 1)));
            Date nextRetry = retry >= properties.getMaxSyncRetries()
                    ? null : new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delay));
            memoryDao.lambdaUpdate()
                    .eq(AiMemory::getId, memoryId)
                    .set(AiMemory::getSyncStatus, FAILED)
                    .set(AiMemory::getRetryCount, retry)
                    .set(AiMemory::getLastError,
                            StringUtils.abbreviate(StringUtils.defaultString(e.getMessage(), e.getClass().getSimpleName()), 500))
                    .set(AiMemory::getNextRetryTime, nextRetry)
                    .update();
            log.warn("AI memory sync failed memoryId={} retry={}/{}", memoryId, retry,
                    properties.getMaxSyncRetries(), e);
        }
    }

    @Scheduled(initialDelayString = "${haochat.ai.memory.sync-initial-delay-ms:30000}",
            fixedDelayString = "${haochat.ai.memory.sync-schedule-ms:60000}")
    public void retryPending() {
        if (!properties.isEnabled()) {
            return;
        }
        Date now = new Date();
        List<AiMemory> memories = memoryDao.lambdaQuery()
                .eq(AiMemory::getStatus, 1)
                .ne(AiMemory::getSyncStatus, READY)
                .lt(AiMemory::getRetryCount, properties.getMaxSyncRetries())
                .and(query -> query.isNull(AiMemory::getNextRetryTime)
                        .or().le(AiMemory::getNextRetryTime, now))
                .orderByAsc(AiMemory::getId)
                .last("limit " + Math.max(1, properties.getSyncBatchSize()))
                .list();
        memories.forEach(memory -> syncNow(memory.getId()));
    }

    public void resetFailed(Long memoryId) {
        memoryDao.lambdaUpdate()
                .eq(AiMemory::getId, memoryId)
                .set(AiMemory::getSyncStatus, PENDING)
                .set(AiMemory::getRetryCount, 0)
                .set(AiMemory::getLastError, null)
                .set(AiMemory::getNextRetryTime, new Date())
                .update();
        syncNow(memoryId);
    }

    private boolean claim(Long memoryId) {
        Date now = new Date();
        Date leaseUntil = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
        return memoryDao.lambdaUpdate()
                .eq(AiMemory::getId, memoryId)
                .ne(AiMemory::getSyncStatus, READY)
                .lt(AiMemory::getRetryCount, properties.getMaxSyncRetries())
                .and(query -> query.isNull(AiMemory::getNextRetryTime)
                        .or().le(AiMemory::getNextRetryTime, now))
                .set(AiMemory::getSyncStatus, SYNCING)
                .set(AiMemory::getNextRetryTime, leaseUntil)
                .update();
    }

    private List<String> entities(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid memory entities JSON", e);
        }
    }
}

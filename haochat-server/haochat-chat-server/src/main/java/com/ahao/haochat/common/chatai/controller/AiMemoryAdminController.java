package com.ahao.haochat.common.chatai.controller;

import com.ahao.haochat.common.chatai.dao.AiMemoryDao;
import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.memory.AiMemorySyncService;
import com.ahao.haochat.common.chatai.memory.Neo4jMemoryGraphClient;
import com.ahao.haochat.common.chatai.memory.QdrantMemoryClient;
import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
import com.ahao.haochat.common.chatai.service.AiModelGateway;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.enums.RoleEnum;
import com.ahao.haochat.common.user.service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Operational visibility and recovery controls for the AI memory pipeline. */
@RestController
@RequestMapping("/capi/ai/memory")
@RequiredArgsConstructor
public class AiMemoryAdminController {
    private final AiMemoryProperties properties;
    private final AiMemoryDao memoryDao;
    private final AiModelGateway modelGateway;
    private final QdrantMemoryClient qdrantMemoryClient;
    private final Neo4jMemoryGraphClient graphClient;
    private final AiMemorySyncService syncService;
    private final IRoleService roleService;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        assertAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("embeddingConfigured", modelGateway.embeddingConfigured());
        result.put("embeddingHealthy", modelGateway.embeddingHealthy(properties.getEmbeddingModel()));
        result.put("qdrantHealthy", qdrantMemoryClient.health());
        result.put("neo4jHealthy", graphClient.health());
        result.put("total", memoryDao.count());
        result.put("ready", count(AiMemorySyncService.READY));
        result.put("pending", count(AiMemorySyncService.PENDING));
        result.put("syncing", count(AiMemorySyncService.SYNCING));
        result.put("failed", count(AiMemorySyncService.FAILED));
        return ApiResult.success(result);
    }

    @PostMapping("/{id}/retry")
    public ApiResult<Void> retry(@PathVariable Long id) {
        assertAdmin();
        AssertUtil.isTrue(memoryDao.getById(id) != null, "记忆记录不存在");
        syncService.resetFailed(id);
        return ApiResult.success();
    }

    private long count(String syncStatus) {
        return memoryDao.lambdaQuery().eq(AiMemory::getSyncStatus, syncStatus).count();
    }

    private void assertAdmin() {
        Long uid = RequestHolder.get().getUid();
        AssertUtil.isTrue(roleService.hasPower(uid, RoleEnum.ADMIN), "没有权限");
    }
}

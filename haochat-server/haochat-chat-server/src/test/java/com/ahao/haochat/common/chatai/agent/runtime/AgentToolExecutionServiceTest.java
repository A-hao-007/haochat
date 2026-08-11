package com.ahao.haochat.common.chatai.agent.runtime;

import com.ahao.haochat.common.chatai.mcp.McpTool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic reliability tests; no network, database or real model required. */
public class AgentToolExecutionServiceTest {

    private final AgentToolExecutionService service = new AgentToolExecutionService();

    @After
    public void tearDown() {
        service.shutdown();
    }

    @Test
    public void retriesReadOnlyToolOnce() {
        AtomicInteger calls = new AtomicInteger();
        McpTool tool = tool("flakyRead", true, args -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary");
            return "ok";
        });

        AgentToolExecutionService.Result result = service.executeLocal(tool, Map.of());

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(2, result.getAttempts());
        Assert.assertEquals(2, calls.get());
    }

    @Test
    public void neverRetriesWriteTool() {
        AtomicInteger calls = new AtomicInteger();
        McpTool tool = tool("write", false, args -> {
            calls.incrementAndGet();
            throw new IllegalStateException("failed");
        });

        AgentToolExecutionService.Result result = service.executeLocal(tool, Map.of());

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(1, result.getAttempts());
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void opensCircuitAfterThreeFailedExecutions() {
        McpTool tool = tool("brokenWrite", false, args -> {
            throw new IllegalStateException("failed");
        });
        service.executeLocal(tool, Map.of());
        service.executeLocal(tool, Map.of());
        service.executeLocal(tool, Map.of());

        AgentToolExecutionService.Result rejected = service.executeLocal(tool, Map.of());

        Assert.assertFalse(rejected.isSuccess());
        Assert.assertEquals("CIRCUIT_OPEN", rejected.getErrorCode());
        Assert.assertEquals(0, rejected.getAttempts());
    }

    private McpTool tool(String name, boolean readOnly, ToolAction action) {
        return new McpTool() {
            public String name() { return name; }
            public String description() { return name; }
            public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            public String execute(Map<String, Object> args) { return action.execute(args); }
            public boolean isReadOnly() { return readOnly; }
        };
    }

    @FunctionalInterface
    private interface ToolAction {
        String execute(Map<String, Object> args);
    }
}

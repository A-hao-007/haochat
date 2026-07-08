package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.chat.dao.GroupMemberDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.dao.RoomGroupDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.ChatMessageReq;
import com.ahao.haochat.common.chat.domain.vo.request.msg.TextMsgReq;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chatai.log.AiCallLog;
import com.ahao.haochat.common.chatai.log.AiCallLogDao;
import com.ahao.haochat.common.chatai.mcp.McpTool;
import com.ahao.haochat.common.chatai.mcp.McpToolRegistry;
import com.ahao.haochat.common.chatai.properties.AiPromptProperties;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.domain.enums.WSRespTypeEnum;
import com.ahao.haochat.common.user.domain.vo.response.ws.WSAgentStreamChunk;
import com.ahao.haochat.common.user.service.UserService;
import com.ahao.haochat.common.user.service.cache.UserInfoCache;
import com.ahao.haochat.common.user.service.impl.PushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 核心：基于 LangChain4j {@link AiServices} 的 function-calling 编排，最终回复走流式输出。
 * 工具来源 = 本地 MCP 工具（进程内）∪ 外部 MCP 工具，统一由 {@link McpToolProvider} 桥接。
 * 对话记忆落地 {@link RedisChatMemoryStore}，按 token 数裁剪（而不是旧版按"条数"裁剪）。
 *
 * [AI-CONTEXT] 流式回调（onPartialResponse/onToolExecuted/onCompleteResponse/onError）可能运行在
 * 与发起调用不同的线程上，因此本类不使用 ThreadLocal 传递跨回调状态（旧版 toolCallTrace 的做法在这里不可靠），
 * 一律通过 run() 内部的局部变量（闭包捕获）传递，天然线程安全。
 */
@Slf4j
@Service
public class AgentService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOOL_ROUND_TRIPS = 5;
    private static final int MEMORY_MAX_TOKENS = 3000;
    /** jtokkit 不认识 DeepSeek 的模型名，用一个通用 OpenAI 模型名做近似 token 估算，仅用于记忆窗口裁剪，不影响实际调用 */
    private static final String TOKEN_ESTIMATOR_MODEL = "gpt-3.5-turbo";
    /** 单条消息超过这个长度自动分段发送（与 TextMsgReq.content 的 @Size(max=1024) 留出前缀余量） */
    private static final int MAX_SEGMENT_LEN = 800;

    @Autowired
    private DeepSeekProperties props;
    @Autowired
    private McpToolProvider mcpToolProvider;
    @Autowired
    private RedisChatMemoryStore memoryStore;
    @Autowired
    private UserService userService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private PushService pushService;
    @Autowired
    private AiCallLogDao aiCallLogDao;
    @Autowired
    private RoomGroupDao roomGroupDao;
    @Autowired
    private GroupMemberDao groupMemberDao;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private UserInfoCache userInfoCache;
    @Autowired
    private PendingConfirmStore pendingConfirmStore;
    @Autowired
    private McpToolRegistry mcpToolRegistry;
    @Autowired
    private AiPromptProperties promptProperties;

    private static final Set<String> CONFIRM_WORDS = Set.of("确认", "是", "发布", "ok", "OK", "好的", "同意", "确定");
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不要", "cancel", "Cancel");
    private static final String PROMPT_CACHE_KEY = "ai:system_prompt";
    private static final int PROMPT_CACHE_TTL_MINUTES = 5;

    private HaoChatAgent agent;

    @PostConstruct
    public void init() {
        if (!isUse()) {
            log.info("DeepSeek 未配置有效Key，AgentService 不启用");
            return;
        }
        StreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(normalizeBaseUrl(props.getUrl()))
                .apiKey(props.getKey())
                .modelName(props.getModel())
                .maxTokens(props.getMaxTokens())
                .timeout(java.time.Duration.ofSeconds(props.getTimeout()))
                .build();
        OpenAiTokenCountEstimator tokenEstimator = new OpenAiTokenCountEstimator(TOKEN_ESTIMATOR_MODEL);

        this.agent = AiServices.builder(HaoChatAgent.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(mcpToolProvider)
                .chatMemoryProvider(memoryId -> TokenWindowChatMemory.builder()
                        .id(memoryId)
                        .maxTokens(MEMORY_MAX_TOKENS, tokenEstimator)
                        .chatMemoryStore(memoryStore)
                        .build())
                .systemMessageProvider(this::buildSystemMessage)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
    }

    private boolean isUse() {
        return props.isUse() && StringUtils.isNotBlank(props.getKey());
    }

    private String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String suffix = "/chat/completions";
        return url.endsWith(suffix) ? url.substring(0, url.length() - suffix.length()) : url;
    }

    /**
     * 触发一次 Agent 对话。流式场景下消息的落库与推送已经在内部异步完成，
     * 调用方（DeepSeekChatAIHandler.doChat()）不需要再处理返回值，统一返回 null。
     */
    public String run(Message triggerMsg) {
        if (agent == null) {
            return null;
        }
        Long callerUid = triggerMsg.getFromUid();
        Long roomId = triggerMsg.getRoomId();

        // 限流：按uid每小时计数，避免无成本刷爆AI调用
        Long count = RedisUtils.inc(rateLimitKey(callerUid), 1, TimeUnit.HOURS);
        if (count != null && count > props.getLimit()) {
            String text = "你今天找我聊太多次啦，休息一下吧~（每小时最多" + props.getLimit() + "次）";
            sendReply(roomId, triggerMsg, text, false);
            logCall(callerUid, roomId, triggerMsg.getContent(), text, List.of(), 0, null, false, null);
            return null;
        }

        // 如果上一轮触发了需要确认的高风险动作，本条消息先按"确认/取消/其它"三分支处理，不进入正式Agent循环
        PendingConfirmation pending = pendingConfirmStore.load(roomId, callerUid);
        if (pending != null) {
            ConfirmResult confirmResult = handlePendingConfirmation(pending, roomId, callerUid, triggerMsg.getContent());
            if (confirmResult != null) {
                sendReply(roomId, triggerMsg, confirmResult.text, false);
                logCall(callerUid, roomId, triggerMsg.getContent(), confirmResult.text, List.of(), 0, null, true, confirmResult.matched);
                return null;
            }
            // 三个分支都没命中：视为用户放弃了待确认动作，pending已清除，继续走正常流程处理这句新消息
        }

        runStreaming(triggerMsg, callerUid, roomId);
        return null;
    }

    /** streamId -> 发起者uid。用于"停止生成"的归属校验，单实例部署内存表即可（不引入Redis轮询开销） */
    private final Map<String, Long> activeStreams = new ConcurrentHashMap<>();
    /** 用户已请求停止的 streamId 集合，onPartialResponse 每个片段到达时检查 */
    private final Set<String> stoppedStreams = ConcurrentHashMap.newKeySet();

    /**
     * 停止一条正在生成的流式回复。只有发起这条对话的用户能停止自己的流。
     * LangChain4j 的 TokenStream 没有真正的取消句柄，这里的语义是：
     * 停止推送/累积后续片段，并立即用已生成的部分内容收尾落库（模型侧会继续跑完但结果被丢弃）。
     */
    public boolean stopStream(Long uid, String streamId) {
        Long owner = activeStreams.get(streamId);
        if (owner == null || !owner.equals(uid)) {
            return false;
        }
        stoppedStreams.add(streamId);
        return true;
    }

    private void cleanupStream(String streamId) {
        activeStreams.remove(streamId);
        stoppedStreams.remove(streamId);
    }

    /** 正常对话主路径：注册流式回调后触发，不阻塞调用线程 */
    private void runStreaming(Message triggerMsg, Long callerUid, Long roomId) {
        long start = System.currentTimeMillis();
        String senderName = resolveSenderName(callerUid);
        String memoryId = memoryId(roomId, callerUid);
        String streamId = UUID.randomUUID().toString();
        String prefix = "@" + senderName + " ";

        // 累积的是模型原始输出，不含前缀；sendReply() 落库时会统一加前缀，这里流式展示时提前推一次前缀，保证视觉和最终文本一致
        StringBuilder fullText = new StringBuilder();
        List<String> toolCallNames = new CopyOnWriteArrayList<>();
        AtomicBoolean needsConfirmationFlag = new AtomicBoolean(false);
        // complete/error/用户停止 三个收尾路径互斥，保证只落库一次
        AtomicBoolean finalized = new AtomicBoolean(false);

        TokenStream tokenStream;
        try {
            tokenStream = agent.chat(memoryId, "[" + senderName + "]: " + triggerMsg.getContent());
        } catch (Exception e) {
            log.warn("发起AI流式对话失败 roomId={} uid={}", roomId, callerUid, e);
            sendReply(roomId, triggerMsg, "AI暂时遇到问题，请稍后重试~", false);
            logCall(callerUid, roomId, triggerMsg.getContent(), null, toolCallNames,
                    (int) (System.currentTimeMillis() - start), e.getMessage(), false, null);
            return;
        }

        activeStreams.put(streamId, callerUid);
        pushStreamChunk(roomId, streamId, callerUid, prefix);

        tokenStream
                .onPartialResponse(chunk -> {
                    if (stoppedStreams.contains(streamId)) {
                        // 用户点了停止：用已生成的部分内容立即收尾，后续片段全部丢弃
                        if (finalized.compareAndSet(false, true)) {
                            String text = fullText.toString();
                            sendReply(roomId, triggerMsg, StringUtils.isBlank(text) ? "（已停止）" : text, false);
                            logCall(callerUid, roomId, triggerMsg.getContent(), text, toolCallNames,
                                    (int) (System.currentTimeMillis() - start), null, false, null);
                        }
                        return;
                    }
                    fullText.append(chunk);
                    pushStreamChunk(roomId, streamId, callerUid, chunk);
                })
                .onToolExecuted(exec -> {
                    String toolName = exec.request().name();
                    toolCallNames.add(toolName);
                    McpTool tool = mcpToolRegistry.get(toolName);
                    if (tool != null && tool.needsConfirmation()) {
                        needsConfirmationFlag.set(true);
                    }
                })
                .onCompleteResponse(response -> {
                    if (finalized.compareAndSet(false, true)) {
                        String text = fullText.toString();
                        sendReply(roomId, triggerMsg, text, needsConfirmationFlag.get());
                        logCall(callerUid, roomId, triggerMsg.getContent(), text, toolCallNames,
                                (int) (System.currentTimeMillis() - start), null, needsConfirmationFlag.get(), null);
                    }
                    cleanupStream(streamId);
                })
                .onError(err -> {
                    if (finalized.compareAndSet(false, true)) {
                        log.warn("AI流式对话出错 roomId={} uid={}", roomId, callerUid, err);
                        String text = fullText.toString();
                        if (StringUtils.isNotBlank(text)) {
                            // 已经生成了部分内容，残缺回复也比完全没反应强
                            sendReply(roomId, triggerMsg, text, false);
                        } else {
                            sendReply(roomId, triggerMsg, "AI暂时遇到问题，请稍后重试~", false);
                        }
                        logCall(callerUid, roomId, triggerMsg.getContent(), text, toolCallNames,
                                (int) (System.currentTimeMillis() - start), err.getMessage(), needsConfirmationFlag.get(), null);
                    }
                    cleanupStream(streamId);
                })
                .start();
    }

    private void pushStreamChunk(Long roomId, String streamId, Long callerUid, String chunk) {
        try {
            WSAgentStreamChunk data = new WSAgentStreamChunk();
            data.setRoomId(roomId);
            data.setStreamId(streamId);
            data.setFromUid(props.getUid());
            data.setChunk(chunk);
            WSBaseResp<WSAgentStreamChunk> resp = new WSBaseResp<>();
            resp.setType(WSRespTypeEnum.AGENT_STREAM_CHUNK.getType());
            resp.setData(data);
            // 必须用同步保序推送：sendPushMsg 内部对每次调用都单独走线程池，高频连续调用时线程池不保证执行顺序，
            // 会导致流式片段乱序到达前端（已实测验证过这个问题）
            pushService.sendPushMsgSync(resp, callerUid);
        } catch (Exception e) {
            log.warn("推送AI流式片段失败 roomId={} streamId={}", roomId, streamId, e);
        }
    }

    private static class ConfirmResult {
        final boolean matched;
        final String text;

        ConfirmResult(boolean matched, String text) {
            this.matched = matched;
            this.text = text;
        }
    }

    /**
     * 处理"待确认动作"命中的这一句用户回复。
     * 返回 null 表示确认/取消关键字都没命中——放弃这个待确认动作（已清除），交给正常流程重新处理这句话。
     */
    private ConfirmResult handlePendingConfirmation(PendingConfirmation pending, Long roomId, Long uid, String userText) {
        String text = userText == null ? "" : userText.trim();
        if (CONFIRM_WORDS.contains(text)) {
            pendingConfirmStore.clear(roomId, uid);
            return new ConfirmResult(true, executeConfirmedTool(pending, roomId, uid));
        }
        if (CANCEL_WORDS.contains(text)) {
            pendingConfirmStore.clear(roomId, uid);
            return new ConfirmResult(false, "已取消。");
        }
        pendingConfirmStore.clear(roomId, uid);
        return null;
    }

    /**
     * 确认按钮点击 / 文字确认路径共用的公开入口：确认或取消一个待确认的高风险动作。
     * pending 已经不存在时（重复点击/竞态）返回"没有待确认的操作了"，不会重复执行工具。
     */
    public String resolvePendingConfirmation(Long uid, Long roomId, boolean confirmed) {
        PendingConfirmation pending = pendingConfirmStore.load(roomId, uid);
        if (pending == null) {
            return "没有待确认的操作了";
        }
        pendingConfirmStore.clear(roomId, uid);
        String resultText = confirmed ? executeConfirmedTool(pending, roomId, uid) : "已取消。";
        // 这条回执也走统一的发送入口，方便前端识别（AI机器人发出、走正常WS推送）
        Message pseudoTrigger = new Message();
        pseudoTrigger.setRoomId(roomId);
        pseudoTrigger.setFromUid(uid);
        sendReply(roomId, pseudoTrigger, resultText, false);
        return resultText;
    }

    private String executeConfirmedTool(PendingConfirmation pending, Long roomId, Long uid) {
        McpTool tool = mcpToolRegistry.get(pending.getToolName());
        if (tool == null) {
            return "确认失败：工具不存在";
        }
        Map<String, Object> args;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(pending.getArgsJson(), Map.class);
            args = parsed;
        } catch (Exception e) {
            args = new HashMap<>();
        }
        args.put("_callerUid", uid);
        args.put("_roomId", roomId);
        try {
            return tool.execute(args);
        } catch (Exception e) {
            log.warn("确认后执行工具 {} 失败", pending.getToolName(), e);
            return "执行失败: " + e.getMessage();
        }
    }

    private String memoryId(Long roomId, Long uid) {
        return roomId + ":" + uid;
    }

    private String rateLimitKey(Long uid) {
        return "ai:limit:" + uid;
    }

    /** [AI-CONTEXT] systemMessageProvider 收到的是 memoryId（"roomId:uid"），从中解析出 roomId 拼接会话成员映射表 */
    private String buildSystemMessage(Object memoryIdObj) {
        String memoryId = String.valueOf(memoryIdObj);
        String[] parts = memoryId.split(":", 2);
        StringBuilder sb = new StringBuilder(basePrompt());
        if (parts.length == 2) {
            try {
                Long roomId = Long.parseLong(parts[0]);
                String roster = buildMemberRoster(roomId);
                if (roster != null) {
                    sb.append("\n当前会话成员（昵称→uid）：").append(roster);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return sb.toString();
    }

    // [AI-CONTEXT] 明确身份与触发方式，避免模型把用户名当uid去调用工具（如"查阿哈的uid"类错误）
    private static final String DEFAULT_PROMPT =
            "你是 HaoChat 的智能助手，只在被@或在与你的单聊中才会被触发回复，不会主动参与无关对话。"
                    + "用户消息前的 [昵称] 是发送者的真实身份，已由系统确认，你不需要也不能查询、猜测或验证提问者自己的身份/uid。"
                    + "你可以使用提供的工具来查询天气、查询好友在线状态、搜索/获取当前会话的聊天记录（想总结聊天内容时用getRecentMessages取原始消息自己归纳，不需要额外的总结工具）、"
                    + "代发消息、发布群公告、创建定时提醒。"
                    + "这些工具如果需要uid参数，必须是数字类型的真实uid，禁止把用户昵称当作uid传入；"
                    + "每次对话开头如果提供了'当前会话成员'的昵称→uid对照表，提到的人在表里就直接用对应uid；"
                    + "不在表里就调用 searchUserByName 按昵称查询，找到了再继续，找不到才如实告知不认识这个人，不要猜测uid。"
                    + "代发消息、发布群公告这类工具调用后不会立即生效，而是会提示你把方案转述给用户确认，"
                    + "你只需要正常调用工具、把工具返回的确认提示原样转达给用户即可，不需要额外解释这个机制。"
                    + "需要实时信息或执行操作时请调用对应工具；能直接回答的就直接回答，回复简洁友好。"
                    // 前端已对AI消息单独做markdown渲染（marked+DOMPurify），可以放心用结构化语法；
                    // 但聊天气泡宽度有限，提醒模型别写大表格、少用一级标题这类"文档感"很重的排版
                    + "你的回复支持markdown渲染：需要分点时用列表，代码用```代码块，强调用**加粗**。"
                    + "但这是聊天气泡不是文档：篇幅尽量简短口语化，优先用列表和加粗而不是多级标题，"
                    + "避免宽表格（气泡宽度有限），简单一两句话能说清的就不要强行分点。";

    /**
     * system prompt 挪到配置（haochat.ai.system-prompt，为空则用内置默认文案），经 Redis 缓存5分钟。
     * 这层缓存的意义：如果将来需要不重启应用就调整 prompt，直接改 Redis 里这个 key 即可，
     * 缓存过期后会自动回落到配置值——不是为了性能（读配置对象本身没有性能问题）。
     */
    private String basePrompt() {
        String cached = RedisUtils.getStr(PROMPT_CACHE_KEY);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        String prompt = StringUtils.isNotBlank(promptProperties.getSystemPrompt())
                ? promptProperties.getSystemPrompt() : DEFAULT_PROMPT;
        RedisUtils.set(PROMPT_CACHE_KEY, prompt, PROMPT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return prompt;
    }

    /**
     * [AI-CONTEXT] 自动注入"当前会话成员"昵称→uid映射，解决模型不知道某人uid时拒绝调用工具的问题。
     * 群聊：查该群全部成员；单聊：只有对话双方。查不到时返回 null，不注入这条内容。
     */
    private String buildMemberRoster(Long roomId) {
        try {
            List<Long> uidList;
            var roomGroup = roomGroupDao.getByRoomId(roomId);
            if (roomGroup != null) {
                uidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            } else {
                var roomFriend = roomFriendDao.getByRoomId(roomId);
                if (roomFriend == null) {
                    return null;
                }
                uidList = Arrays.asList(roomFriend.getUid1(), roomFriend.getUid2());
            }
            if (uidList == null || uidList.isEmpty()) {
                return null;
            }
            Map<Long, User> userMap = userInfoCache.getBatch(uidList);
            List<String> parts = new ArrayList<>();
            for (Long uid : uidList) {
                User user = userMap.get(uid);
                if (user != null && user.getName() != null) {
                    parts.add(user.getName() + "(" + uid + ")");
                }
            }
            return parts.isEmpty() ? null : String.join("、", parts);
        } catch (Exception e) {
            log.warn("构建会话成员映射失败 roomId={}", roomId, e);
            return null;
        }
    }

    private String resolveSenderName(Long uid) {
        try {
            var userInfo = userService.getUserInfo(uid);
            if (userInfo != null && userInfo.getName() != null && !userInfo.getName().isEmpty()) {
                return userInfo.getName();
            }
        } catch (Exception e) {
            log.warn("查询发送者昵称失败 uid={}", uid, e);
        }
        return "用户";
    }

    /**
     * 统一的AI回复发送入口，替代旧版依赖 AbstractChatAIHandler.answerMsg() 的方式
     * （流式场景下落库时机分散在多个异步回调里，不适合再走基类那套"同步返回文本再由调用方发送"的模型）。
     * 自动加 "@发送者 " 前缀、超过 MAX_SEGMENT_LEN 自动分段，仅最后一段带确认标记。
     */
    private void sendReply(Long roomId, Message triggerMsg, String rawText, boolean needsConfirmation) {
        if (StringUtils.isBlank(rawText)) {
            return;
        }
        String senderName = resolveSenderName(triggerMsg.getFromUid());
        String text = "@" + senderName + " " + rawText;
        if (text.length() <= MAX_SEGMENT_LEN) {
            sendReplySegment(roomId, triggerMsg, text, needsConfirmation);
            return;
        }
        int len = text.length();
        int count = (len + MAX_SEGMENT_LEN - 1) / MAX_SEGMENT_LEN;
        for (int i = 0; i < count; i++) {
            int start = i * MAX_SEGMENT_LEN;
            int end = Math.min(start + MAX_SEGMENT_LEN, len);
            boolean segNeedsConfirmation = needsConfirmation && (i == count - 1);
            sendReplySegment(roomId, triggerMsg, text.substring(start, end), segNeedsConfirmation);
        }
    }

    private void sendReplySegment(Long roomId, Message triggerMsg, String text, boolean needsConfirmation) {
        try {
            ChatMessageReq req = new ChatMessageReq();
            req.setRoomId(roomId);
            req.setMsgType(MessageTypeEnum.TEXT.getType());
            TextMsgReq body = new TextMsgReq();
            body.setContent(text);
            if (triggerMsg.getId() != null) {
                body.setReplyMsgId(triggerMsg.getId());
            }
            body.setAtUidList(Collections.singletonList(triggerMsg.getFromUid()));
            body.setNeedsConfirmation(needsConfirmation);
            req.setBody(body);
            chatService.sendMsg(req, props.getUid());
        } catch (Exception e) {
            log.warn("AI回复发送失败 roomId={}", roomId, e);
        }
    }

    private void logCall(Long uid, Long roomId, String userInput, String reply,
                          List<String> toolCallNames, int latencyMs, String errorMsg,
                          Boolean needsConfirmation, Boolean confirmed) {
        try {
            String toolCallsJson = toolCallNames.isEmpty() ? null : MAPPER.writeValueAsString(toolCallNames);
            AiCallLog callLog = AiCallLog.builder()
                    .uid(uid)
                    .roomId(roomId)
                    .userInput(truncate(userInput, 2048))
                    .finalReply(truncate(reply, 4000))
                    .toolCalls(toolCallsJson)
                    .turnCount(toolCallNames.size() + 1)
                    .latencyMs(latencyMs)
                    .success(errorMsg == null ? 1 : 0)
                    .errorMsg(truncate(errorMsg, 512))
                    .needsConfirmation(Boolean.TRUE.equals(needsConfirmation) ? 1 : 0)
                    .confirmed(confirmed == null ? null : (confirmed ? 1 : 0))
                    .createTime(new Date())
                    .build();
            aiCallLogDao.save(callLog);
        } catch (Exception e) {
            log.warn("保存AI调用日志失败", e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

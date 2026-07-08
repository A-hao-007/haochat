package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.log.AiCallLog;
import com.ahao.haochat.common.chatai.log.AiCallLogDao;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 评测集：手动运行的端到端回归（不在 mvn package -DskipTests 的默认流程里跑，
 * 需要真实 DeepSeek Key + 已存在的测试用户/会话数据才能跑通，风格与项目里其它需要真实环境的
 * DaoTest 一致——单个/整体手动执行看输出，不是每次构建强制通过的 CI 断言）。
 *
 * 用法：把下面的 TEST_UID / TEST_ROOM_ID 换成你测试环境里真实存在、且互为好友或同群的两个账号，
 * 跑 runEvalSuite()，看日志里的 PASS/FAIL 汇总。
 *
 * 后续维护：从 ai_call_log 表挑真实出现过的 badcase，追加到 CASES 里，防止同类问题回归。
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class AgentEvalTest {

    /** 触发消息的发送者 uid，需替换为测试环境真实存在的账号 */
    private static final Long TEST_UID = 10001L;
    /** 发送消息所在的会话 roomId，需替换为该账号真实所在的单聊/群聊房间 */
    private static final Long TEST_ROOM_ID = 1L;

    @Autowired
    private AgentService agentService;
    @Autowired
    private AiCallLogDao aiCallLogDao;

    private static class EvalCase {
        final String input;
        /** 期望这次调用命中的工具名，null 表示不检查（比如纯闲聊场景） */
        final String expectedTool;
        /** 期望回复文本包含的关键词，null 表示不检查 */
        final String expectedReplyContains;

        EvalCase(String input, String expectedTool, String expectedReplyContains) {
            this.input = input;
            this.expectedTool = expectedTool;
            this.expectedReplyContains = expectedReplyContains;
        }
    }

    private static final List<EvalCase> CASES = List.of(
            new EvalCase("你好", null, null),
            new EvalCase("你是谁", null, "HaoChat"),
            new EvalCase("今天天气怎么样", "getWeather", null),
            new EvalCase("帮我总结一下这个群最近在聊什么", "getRecentMessages", null),
            new EvalCase("帮我查一下uid=999999999这个人在不在线", "queryUserStatus", null),
            new EvalCase("张三丰在不在线", "searchUserByName", null),
            new EvalCase("提醒我明天早上9点开会", "createReminder", "提醒"),
            new EvalCase("帮我发布一条群公告：周五晚上8点开会", "createGroupNotice", "确认"),
            new EvalCase("你能帮我订机票吗", null, null),
            new EvalCase("1+1等于几", null, null)
    );

    @Test
    public void runEvalSuite() {
        int total = CASES.size();
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (EvalCase c : CASES) {
            Message msg = Message.builder()
                    .fromUid(TEST_UID)
                    .roomId(TEST_ROOM_ID)
                    .content(c.input)
                    .build();
            String reply;
            try {
                reply = agentService.run(msg);
            } catch (Exception e) {
                log.warn("[FAIL] input={} 抛出异常", c.input, e);
                failures.add(c.input + " -> 异常: " + e.getMessage());
                continue;
            }

            boolean replyOk = reply != null
                    && (c.expectedReplyContains == null || reply.contains(c.expectedReplyContains));
            boolean toolOk = c.expectedTool == null || calledTool(TEST_UID, TEST_ROOM_ID, c.expectedTool);

            if (replyOk && toolOk) {
                passed++;
                log.info("[PASS] input={} reply={}", c.input, reply);
            } else {
                failures.add(c.input + " -> reply=" + reply + "（期望工具=" + c.expectedTool
                        + ", 期望关键词=" + c.expectedReplyContains + "）");
                log.warn("[FAIL] input={} reply={}", c.input, reply);
            }
        }
        log.info("========== Agent 评测集: {}/{} 通过 ==========", passed, total);
        if (!failures.isEmpty()) {
            log.warn("失败用例:\n{}", String.join("\n", failures));
        }
    }

    /** 查这个 uid+roomId 最近一条调用日志，确认是否命中了期望的工具 */
    private boolean calledTool(Long uid, Long roomId, String toolName) {
        AiCallLog latest = aiCallLogDao.lambdaQuery()
                .eq(AiCallLog::getUid, uid)
                .eq(AiCallLog::getRoomId, roomId)
                .orderByDesc(AiCallLog::getId)
                .last("limit 1")
                .one();
        return latest != null && latest.getToolCalls() != null && latest.getToolCalls().contains(toolName);
    }
}

package com.ahao.haochat.common.common.constant;

/**
 * @author zhongzb create on 2021/06/10
 */
public class RedisKey {
    private static final String BASE_KEY = "haochat:";
    private static final String SEPARATOR = ":";

    /**
     * 在线用户列表
     */
    public static final String ONLINE_UID_ZET = "online";

    /**
     * 离线用户列表
     */
    public static final String OFFLINE_UID_ZET = "offline";

    /**
     * 热门房间列表
     */
    public static final String HOT_ROOM_ZET = "hotRoom";

    /**
     * 用户信息
     */
    public static final String USER_INFO_STRING = "userInfo:uid_%d";

    /**
     * 房间详情
     */
    public static final String ROOM_INFO_STRING = "roomInfo:roomId_%d";

    /**
     * 群组详情
     */
    public static final String GROUP_INFO_STRING = "groupInfo:roomId_%d";

    /**
     * 群组详情
     */
    public static final String GROUP_FRIEND_STRING = "groupFriend:roomId_%d";

    /**
     * 用户token存放
     */
    public static final String USER_TOKEN_STRING = "userToken:uid_%d";

    /**
     * 用户的信息更新时间
     */
    public static final String USER_MODIFY_STRING = "userModify:uid_%d";

    /**
     * 用户的信息汇总
     */
    public static final String USER_SUMMARY_STRING = "userSummary:uid_%d";

    /**
     * 用户GPT聊天次数
     */
    public static final String USER_CHAT_NUM = "useChatGPTNum:uid_%d";

    public static final String USER_CHAT_CONTEXT = "useChatGPTContext:uid_%d_roomId_%d";

    /**
     * 保存Open id
     */
    public static final String OPEN_ID_STRING = "openid:%s";

    /**
     * WeChat login code sequence.
     */
    public static final String LOGIN_CODE_STRING = "loginCode";


    /**
     * 用户上次使用GLM使用时间
     */
    public static final String USER_GLM2_TIME_LAST = "userGLM2UseTime:uid_%d";

    /**
     * Email verification code. scene must distinguish register/login/bind/forgot.
     */
    public static final String EMAIL_CODE_STRING = "auth:email:code:%s:%s";

    /**
     * Login failure counter, keyed by ip and principal.
     */
    public static final String LOGIN_FAIL_STRING = "auth:login:fail:%s:%s";

    /**
     * AI short-term chat memory.
     */
    public static final String AI_CONTEXT_STRING = "ai:context:%s";

    /**
     * Pending high-risk AI tool confirmation.
     */
    public static final String AI_PENDING_CONFIRM_STRING = "ai:pending_confirm:%d:%d";

    /**
     * AI system prompt cache.
     */
    public static final String AI_SYSTEM_PROMPT_STRING = "ai:system_prompt";

    /**
     * AI hourly user rate limit.
     */
    public static final String AI_RATE_LIMIT_STRING = "ai:rate:uid_%d";

    public static String getKey(String key, Object... objects) {
        return BASE_KEY + String.format(key, objects);
    }

    public static String join(String first, Object... parts) {
        StringBuilder builder = new StringBuilder(BASE_KEY).append(first);
        for (Object part : parts) {
            builder.append(SEPARATOR).append(part);
        }
        return builder.toString();
    }

}

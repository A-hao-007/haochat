package com.ahao.haochat.common.user.service;

import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.domain.vo.request.ws.WSAuthorize;
import io.netty.channel.Channel;

public interface WebSocketService {
    /**
     * 处理用户登录请求，需要返回一张带code的二维码
     *
     * @param channel
     */
    void handleLoginReq(Channel channel);

    /**
     * 处理所有ws连接的事件
     *
     * @param channel
     */
    void connect(Channel channel);

    /**
     * 处理ws断开连接的事件
     *
     * @param channel
     */
    void removed(Channel channel);

    /**
     * 主动认证登录
     *
     * @param channel
     * @param wsAuthorize
     */
    void authorize(Channel channel, WSAuthorize wsAuthorize);

    /**
     * 扫码用户登录成功通知,清除本地Cache中的loginCode和channel的关系
     */
    Boolean scanLoginSuccess(Integer loginCode, Long uid);

    /**
     * 通知用户扫码成功
     *
     * @param loginCode
     */
    Boolean scanSuccess(Integer loginCode);

    /**
     * 推动消息给所有在线的人
     *
     * @param wsBaseResp 发送的消息体
     * @param skipUid    需要跳过的人
     */
    void sendToAllOnline(WSBaseResp<?> wsBaseResp, Long skipUid);

    /**
     * 推动消息给所有在线的人
     *
     * @param wsBaseResp 发送的消息体
     */
    void sendToAllOnline(WSBaseResp<?> wsBaseResp);

    void sendToUid(WSBaseResp<?> wsBaseResp, Long uid);

    /**
     * 同步发送（不经过线程池），保证同一批调用按调用顺序真正发起 writeAndFlush。
     * sendToUid() 内部对每次推送都单独 execute() 到线程池，线程池不保证任务执行顺序——
     * 低频通知场景下无害，但高频密集推送（如 AI 流式回复片段）必须用这个方法，否则会乱序到达客户端。
     *
     * @param wsBaseResp 发送的消息体
     * @param uid        目标用户
     */
    void sendToUidSync(WSBaseResp<?> wsBaseResp, Long uid);

}

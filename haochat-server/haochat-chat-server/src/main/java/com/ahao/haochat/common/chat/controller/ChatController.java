package com.ahao.haochat.common.chat.controller;


import com.ahao.haochat.common.chat.domain.dto.MsgReadInfoDTO;
import com.ahao.haochat.common.chat.domain.vo.request.*;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberListResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageReadResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.adapter.MemberAdapter;
import com.ahao.haochat.common.chatai.agent.AgentService;
import com.ahao.haochat.common.chatai.handler.ChatAIHandlerFactory;
import com.ahao.haochat.common.common.annotation.FrequencyControl;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.BlackTypeEnum;
import com.ahao.haochat.common.user.service.cache.UserCache;
import com.ahao.haochat.common.user.service.cache.UserInfoCache;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 群聊相关接口
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
@RestController
@RequestMapping("/capi/chat")
@Api(tags = "聊天室相关接口")
@Slf4j
public class ChatController {
    @Autowired
    private ChatService chatService;
    @Autowired
    private UserCache userCache;
    @Autowired
    private UserInfoCache userInfoCache;
    @Autowired
    private AgentService agentService;

    private Set<String> getBlackUidSet() {
        return userCache.getBlackMap().getOrDefault(BlackTypeEnum.UID.getType(), new HashSet<>());
    }

    @GetMapping("/public/msg/page")
    @ApiOperation("消息列表")
//    @FrequencyControl(time = 120, count = 20, target = FrequencyControl.Target.IP)
    public ApiResult<CursorPageBaseResp<ChatMessageResp>> getMsgPage(@Valid ChatMessagePageReq request) {
        CursorPageBaseResp<ChatMessageResp> msgPage = chatService.getMsgPage(request, RequestHolder.get().getUid());
        filterBlackMsg(msgPage);
        return ApiResult.success(msgPage);
    }

    private void filterBlackMsg(CursorPageBaseResp<ChatMessageResp> memberPage) {
        Set<String> blackMembers = getBlackUidSet();
        memberPage.getList().removeIf(a -> blackMembers.contains(a.getFromUser().getUid().toString()));
    }

    @PostMapping("/msg")
    @ApiOperation("发送消息")
    @FrequencyControl(time = 5, count = 3, target = FrequencyControl.Target.UID)
    @FrequencyControl(time = 30, count = 5, target = FrequencyControl.Target.UID)
    @FrequencyControl(time = 60, count = 10, target = FrequencyControl.Target.UID)
    public ApiResult<ChatMessageResp> sendMsg(@Valid @RequestBody ChatMessageReq request) {
        Long msgId = chatService.sendMsg(request, RequestHolder.get().getUid());
        //返回完整消息格式，方便前端展示
        return ApiResult.success(chatService.getMsgResp(msgId, RequestHolder.get().getUid()));
    }

    @PutMapping("/msg/mark")
    @ApiOperation("消息标记")
    @FrequencyControl(time = 10, count = 5, target = FrequencyControl.Target.UID)
    public ApiResult<Void> setMsgMark(@Valid @RequestBody ChatMessageMarkReq request) {
        chatService.setMsgMark(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PutMapping("/msg/recall")
    @ApiOperation("撤回消息")
    @FrequencyControl(time = 20, count = 3, target = FrequencyControl.Target.UID)
    public ApiResult<Void> recallMsg(@Valid @RequestBody ChatMessageBaseReq request) {
        chatService.recallMsg(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    // [AUDIT-ADD] B-消息编辑：仅发送者可编辑文本消息（频控复用撤回的 20s/3 次）
    @PutMapping("/msg/edit")
    @ApiOperation("编辑消息")
    @FrequencyControl(time = 20, count = 3, target = FrequencyControl.Target.UID)
    public ApiResult<Void> editMsg(@Valid @RequestBody ChatMessageEditReq request) {
        chatService.editMsg(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @GetMapping("/msg/read/page")
    @ApiOperation("消息的已读未读列表")
    public ApiResult<CursorPageBaseResp<ChatMessageReadResp>> getReadPage(@Valid ChatMessageReadReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(chatService.getReadPage(uid, request));
    }

    @GetMapping("/msg/read")
    @ApiOperation("获取消息的已读未读总数")
    public ApiResult<Collection<MsgReadInfoDTO>> getReadInfo(@Valid ChatMessageReadInfoReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(chatService.getMsgReadInfo(uid, request));
    }

    @PutMapping("/msg/read")
    @ApiOperation("消息阅读上报")
    public ApiResult<Void> msgRead(@Valid @RequestBody ChatMessageMemberReq request) {
        Long uid = RequestHolder.get().getUid();
        chatService.msgRead(uid, request);
        return ApiResult.success();
    }

    @GetMapping("/public/ai/bots")
    @ApiOperation("已启用的AI助手列表，用于@候选人/前端识别AI机器人uid")
    public ApiResult<List<ChatMemberListResp>> getAiBots() {
        List<Long> aiUidList = ChatAIHandlerFactory.getAllAIUserIds();
        Map<Long, User> aiUserMap = userInfoCache.getBatch(aiUidList);
        return ApiResult.success(MemberAdapter.buildMemberList(aiUserMap));
    }

    @PutMapping("/msg/confirm")
    @ApiOperation("确认/取消AI助手发起的高风险操作提议")
    @FrequencyControl(time = 10, count = 10, target = FrequencyControl.Target.UID)
    public ApiResult<Void> confirmAgentAction(@Valid @RequestBody ChatMsgConfirmReq request) {
        Long uid = RequestHolder.get().getUid();
        agentService.resolvePendingConfirmation(uid, request.getRoomId(), request.getConfirmed());
        return ApiResult.success();
    }

    @PutMapping("/ai/stop")
    @ApiOperation("停止AI流式回复（只能停止自己发起的对话）")
    @FrequencyControl(time = 10, count = 10, target = FrequencyControl.Target.UID)
    public ApiResult<Void> stopAgentStream(@Valid @RequestBody ChatAiStopReq request) {
        Long uid = RequestHolder.get().getUid();
        agentService.stopStream(uid, request.getStreamId());
        return ApiResult.success();
    }
}


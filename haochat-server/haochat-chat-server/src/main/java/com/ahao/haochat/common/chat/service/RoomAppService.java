package com.ahao.haochat.common.chat.service;

import com.ahao.haochat.common.chat.domain.vo.request.*;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberDelReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberReq;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberListResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatRoomResp;
import com.ahao.haochat.common.chat.domain.vo.response.MemberResp;
import com.ahao.haochat.common.chat.domain.entity.GroupOperationLog;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.user.domain.vo.response.ws.ChatMemberResp;

import java.util.List;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-22
 */
public interface RoomAppService {
    /**
     * 获取会话列表--支持未登录态
     */
    CursorPageBaseResp<ChatRoomResp> getContactPage(CursorPageBaseReq request, Long uid);

    /**
     * 获取群组信息
     */
    MemberResp getGroupDetail(Long uid, long roomId);

    CursorPageBaseResp<ChatMemberResp> getMemberPage(MemberReq request);

    List<ChatMemberListResp> getMemberList(Long uid, ChatMessageMemberReq request);

    void delMember(Long uid, MemberDelReq request);

    void addMember(Long uid, MemberAddReq request);

    Long addGroup(Long uid, GroupAddReq request);

    ChatRoomResp getContactDetail(Long uid, Long roomId);

    ChatRoomResp getContactDetailByFriend(Long uid, Long friendUid);

    void pinContact(Long uid, Long roomId, Boolean pinned);
    void muteContact(Long uid, Long roomId, Boolean muted);
    void deleteContact(Long uid, Long roomId);

    /** 更新群公告（群主/管理员） */
    void updateNotice(Long uid, Long roomId, String notice);

    /** 标记当前用户已读最新群公告 */
    void markNoticeRead(Long uid, Long roomId);

    /** 更新我在群里的昵称 */
    void updateNickname(Long uid, Long roomId, String nickname);

    /** 我的群组列表（创建的+加入的） */
    List<ChatRoomResp> getMyGroupList(Long uid);

    /** 群操作日志 */
    CursorPageBaseResp<GroupOperationLog> getGroupLogPage(Long uid, GroupLogPageReq request);
}

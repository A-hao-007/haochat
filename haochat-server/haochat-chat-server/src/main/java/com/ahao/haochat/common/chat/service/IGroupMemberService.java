package com.ahao.haochat.common.chat.service;

import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminRevokeReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberExitReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberMuteReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.TransferLordReq;

/**
 * <p>
 * 群成员表 服务类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
public interface IGroupMemberService {
    /**
     * 增加管理员
     *
     * @param uid     用户ID
     * @param request 请求信息
     */
    void addAdmin(Long uid, AdminAddReq request);

    /**
     * 撤销管理员
     *
     * @param uid     用户ID
     * @param request 请求信息
     */
    void revokeAdmin(Long uid, AdminRevokeReq request);

    /**
     * 退出群聊
     *
     * @param uid     用户ID
     * @param request 请求信息
     */
    void exitGroup(Long uid, MemberExitReq request);

    /**
     * 转让群主
     *
     * @param uid     当前群主用户ID
     * @param request 请求信息
     */
    void transferLord(Long uid, TransferLordReq request);

    void muteMember(Long uid, MemberMuteReq request);

    void unmuteMember(Long uid, MemberMuteReq request);
}

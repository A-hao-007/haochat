package com.ahao.haochat.common.user.service;

import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.request.PageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.domain.vo.response.PageBaseResp;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApplyReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApproveReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendCheckReq;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendApplyResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendCheckResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendUnreadResp;

/**
 * @author : limeng
 * @description : 好友
 * @date : 2023/07/19
 */
public interface FriendService {

    /**
     * 检查
     * 检查是否是自己好友
     *
     * @param request 请求
     * @param uid     uid
     * @return {@link FriendCheckResp}
     */
    FriendCheckResp check(Long uid, FriendCheckReq request);

    /**
     * 应用
     * 申请好友
     *
     * @param request 请求
     * @param uid     uid
     */
    void apply(Long uid, FriendApplyReq request);

    /**
     * 分页查询好友申请
     *
     * @param request 请求
     * @return {@link PageBaseResp}<{@link FriendApplyResp}>
     */
    PageBaseResp<FriendApplyResp> pageApplyFriend(Long uid, PageBaseReq request);

    PageBaseResp<FriendApplyResp> pageSentApplyFriend(Long uid, PageBaseReq request);

    /**
     * 申请未读数
     *
     * @return {@link FriendUnreadResp}
     */
    FriendUnreadResp unread(Long uid);

    /**
     * 同意好友申请
     *
     * @param uid     uid
     * @param request 请求
     */
    void applyApprove(Long uid, FriendApproveReq request);

    void applyReject(Long uid, FriendApproveReq request);

    /**
     * 同意好友申请的事务主体。仅供 {@link #applyApprove} 在获取"好友对"分布式锁后经代理调用，
     * 单独暴露是为了让事务生效（自调用不走代理，事务/锁注解会失效）。外部请勿直接调用。
     */
    void doApplyApprove(Long uid, FriendApproveReq request);

    /**
     * 删除好友
     *
     * @param uid       uid
     * @param friendUid 朋友uid
     */
    void deleteFriend(Long uid, Long friendUid);

    CursorPageBaseResp<FriendResp> friendList(Long uid, CursorPageBaseReq request);

    void setRemark(Long uid, Long friendUid, String remark);
}

package com.ahao.haochat.common.user.controller;


import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.request.PageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.domain.vo.response.PageBaseResp;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApplyReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApproveReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendCheckReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendDeleteReq;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendApplyResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendCheckResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendUnreadResp;
import com.ahao.haochat.common.user.service.FriendService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * <p>
 * 好友相关接口
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
@RestController
@RequestMapping("/capi/user/friend")
@Api(tags = "好友相关接口")
@Slf4j
public class FriendController {
    @Resource
    private FriendService friendService;

    @GetMapping("/check")
    @ApiOperation("批量判断是否是自己好友")
    public ApiResult<FriendCheckResp> check(@Valid FriendCheckReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.check(uid, request));
    }

    @PostMapping("/apply")
    @ApiOperation("申请好友")
    public ApiResult<Void> apply(@Valid @RequestBody FriendApplyReq request) {
        Long uid = RequestHolder.get().getUid();
        friendService.apply(uid, request);
        return ApiResult.success();
    }

    @DeleteMapping()
    @ApiOperation("删除好友")
    public ApiResult<Void> delete(@Valid @RequestBody FriendDeleteReq request) {
        Long uid = RequestHolder.get().getUid();
        friendService.deleteFriend(uid, request.getTargetUid());
        return ApiResult.success();
    }

    @GetMapping("/apply/page")
    @ApiOperation("好友申请列表")
    public ApiResult<PageBaseResp<FriendApplyResp>> page(@Valid PageBaseReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.pageApplyFriend(uid, request));
    }

    @GetMapping("/apply/sent/page")
    @ApiOperation("发出的好友申请列表")
    public ApiResult<PageBaseResp<FriendApplyResp>> sentPage(@Valid PageBaseReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.pageSentApplyFriend(uid, request));
    }

    @GetMapping("/apply/unread")
    @ApiOperation("申请未读数")
    public ApiResult<FriendUnreadResp> unread() {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.unread(uid));
    }

    @PutMapping("/apply")
    @ApiOperation("审批同意")
    public ApiResult<Void> applyApprove(@Valid @RequestBody FriendApproveReq request) {
        friendService.applyApprove(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PutMapping("/apply/reject")
    @ApiOperation("审批拒绝")
    public ApiResult<Void> applyReject(@Valid @RequestBody FriendApproveReq request) {
        friendService.applyReject(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @GetMapping("/page")
    @ApiOperation("联系人列表")
    public ApiResult<CursorPageBaseResp<FriendResp>> friendList(@Valid CursorPageBaseReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(friendService.friendList(uid, request));
    }

    @PutMapping("/remark")
    @ApiOperation("设置好友备注")
    public ApiResult<Void> setRemark(@RequestParam Long friendUid, @RequestParam String remark) {
        friendService.setRemark(RequestHolder.get().getUid(), friendUid, remark);
        return ApiResult.success();
    }
}

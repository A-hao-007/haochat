package com.ahao.haochat.common.chat.controller;


import com.ahao.haochat.common.chat.domain.vo.request.*;
import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminRevokeReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberDelReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberExitReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.TransferLordReq;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberListResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatRoomResp;
import com.ahao.haochat.common.chat.domain.vo.response.MemberResp;
import com.ahao.haochat.common.chat.service.IGroupMemberService;
import com.ahao.haochat.common.chat.service.RoomAppService;
import com.ahao.haochat.common.common.domain.vo.request.IdReqVO;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.domain.vo.response.IdRespVO;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.vo.response.ws.ChatMemberResp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 房间相关接口
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
@RestController
@RequestMapping("/capi/room")
@Api(tags = "聊天室相关接口")
@Slf4j
public class RoomController {
    @Autowired
    private RoomAppService roomService;
    @Autowired
    private IGroupMemberService groupMemberService;

    @GetMapping("/public/group")
    @ApiOperation("群组详情")
    public ApiResult<MemberResp> groupDetail(@Valid IdReqVO request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getGroupDetail(uid, request.getId()));
    }

    @GetMapping("/public/group/member/page")
    @ApiOperation("群成员列表")
    public ApiResult<CursorPageBaseResp<ChatMemberResp>> getMemberPage(@Valid MemberReq request) {
        return ApiResult.success(roomService.getMemberPage(request));
    }

    @GetMapping("/group/member/list")
    @ApiOperation("房间内可@的成员列表（群成员+AI助手，单聊则为对方）")
    public ApiResult<List<ChatMemberListResp>> getMemberList(@Valid ChatMessageMemberReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getMemberList(uid, request));
    }

    @DeleteMapping("/group/member")
    @ApiOperation("移除成员")
    public ApiResult<Void> delMember(@Valid @RequestBody MemberDelReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.delMember(uid, request);
        return ApiResult.success();
    }

    @DeleteMapping("/group/member/exit")
    @ApiOperation("退出群聊")
    public ApiResult<Boolean> exitGroup(@Valid @RequestBody MemberExitReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.exitGroup(uid, request);
        return ApiResult.success();
    }

    @PostMapping("/group")
    @ApiOperation("新增群组")
    public ApiResult<IdRespVO> addGroup(@Valid @RequestBody GroupAddReq request) {
        Long uid = RequestHolder.get().getUid();
        Long roomId = roomService.addGroup(uid, request);
        return ApiResult.success(IdRespVO.id(roomId));
    }

    @PostMapping("/group/member")
    @ApiOperation("邀请好友")
    public ApiResult<Void> addMember(@Valid @RequestBody MemberAddReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.addMember(uid, request);
        return ApiResult.success();
    }

    @PutMapping("/group/admin")
    @ApiOperation("添加管理员")
    public ApiResult<Boolean> addAdmin(@Valid @RequestBody AdminAddReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.addAdmin(uid, request);
        return ApiResult.success();
    }

    @DeleteMapping("/group/admin")
    @ApiOperation("撤销管理员")
    public ApiResult<Boolean> revokeAdmin(@Valid @RequestBody AdminRevokeReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.revokeAdmin(uid, request);
        return ApiResult.success();
    }

    @PutMapping("/group/lord")
    @ApiOperation("转让群主")
    public ApiResult<Void> transferLord(@Valid @RequestBody TransferLordReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.transferLord(uid, request);
        return ApiResult.success();
    }

    @PutMapping("/group/notice")
    @ApiOperation("更新群公告")
    public ApiResult<Void> updateNotice(@Valid @RequestBody GroupNoticeReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.updateNotice(uid, request.getRoomId(), request.getNotice());
        return ApiResult.success();
    }

    @PutMapping("/group/notice/read")
    @ApiOperation("标记已读最新群公告")
    public ApiResult<Void> markNoticeRead(@Valid @RequestBody GroupNoticeReadReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.markNoticeRead(uid, request.getRoomId());
        return ApiResult.success();
    }

    @PutMapping("/group/nickname")
    @ApiOperation("更新我在群里的昵称")
    public ApiResult<Void> updateNickname(@Valid @RequestBody GroupNicknameReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.updateNickname(uid, request.getRoomId(), request.getNickname());
        return ApiResult.success();
    }

    @GetMapping("/group/my-list")
    @ApiOperation("我的群组列表（创建的+加入的）")
    public ApiResult<List<ChatRoomResp>> myGroupList() {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getMyGroupList(uid));
    }
}

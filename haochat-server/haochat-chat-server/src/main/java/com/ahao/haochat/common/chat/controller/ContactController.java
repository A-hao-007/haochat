package com.ahao.haochat.common.chat.controller;

import com.ahao.haochat.common.chat.domain.vo.request.ContactFriendReq;
import com.ahao.haochat.common.chat.domain.vo.response.ChatRoomResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.RoomAppService;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.request.IdReqVO;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.utils.RequestHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/capi/chat")
@Api(tags = "聊天室相关接口")
@Slf4j
public class ContactController {
    @Autowired
    private ChatService chatService;
    @Autowired
    private RoomAppService roomService;

    @GetMapping("/public/contact/page")
    @ApiOperation("会话列表")
    public ApiResult<CursorPageBaseResp<ChatRoomResp>> getRoomPage(@Valid CursorPageBaseReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getContactPage(request, uid));
    }

    @GetMapping("/public/contact/detail")
    @ApiOperation("会话详情")
    public ApiResult<ChatRoomResp> getContactDetail(@Valid IdReqVO request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getContactDetail(uid, request.getId()));
    }

    @GetMapping("/public/contact/detail/friend")
    @ApiOperation("会话详情(联系人列表发消息用)")
    public ApiResult<ChatRoomResp> getContactDetailByFriend(@Valid ContactFriendReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getContactDetailByFriend(uid, request.getUid()));
    }

    @PutMapping("/contact/pin")
    @ApiOperation("置顶/取消置顶会话")
    public ApiResult<Void> pinContact(@RequestParam Long roomId, @RequestParam Boolean pinned) {
        roomService.pinContact(RequestHolder.get().getUid(), roomId, pinned);
        return ApiResult.success();
    }

    @PutMapping("/contact/mute")
    @ApiOperation("免打扰/取消免打扰")
    public ApiResult<Void> muteContact(@RequestParam Long roomId, @RequestParam Boolean muted) {
        roomService.muteContact(RequestHolder.get().getUid(), roomId, muted);
        return ApiResult.success();
    }

    @DeleteMapping("/contact")
    @ApiOperation("删除会话")
    public ApiResult<Void> deleteContact(@RequestParam Long roomId) {
        roomService.deleteContact(RequestHolder.get().getUid(), roomId);
        return ApiResult.success();
    }
}

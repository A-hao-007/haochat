package com.ahao.haochat.common.user.controller;


import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.dto.ItemInfoDTO;
import com.ahao.haochat.common.user.domain.dto.SummeryInfoDTO;
import com.ahao.haochat.common.user.domain.enums.RoleEnum;
import com.ahao.haochat.common.user.domain.vo.request.user.*;
import com.ahao.haochat.common.user.domain.vo.response.user.BadgeResp;
import com.ahao.haochat.common.user.domain.vo.response.user.UserInfoResp;
import com.ahao.haochat.common.user.domain.vo.request.auth.BindEmailReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.EmailCodeReq;
import com.ahao.haochat.common.user.service.EmailAuthService;
import com.ahao.haochat.common.user.service.IRoleService;
import com.ahao.haochat.common.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
@RestController
@RequestMapping("/capi/user")
@Api(tags = "用户管理相关接口")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private EmailAuthService emailAuthService;

    @GetMapping("/userInfo")
    @ApiOperation("用户详情")
    public ApiResult<UserInfoResp> getUserInfo() {
        return ApiResult.success(userService.getUserInfo(RequestHolder.get().getUid()));
    }

    @PostMapping("/public/summary/userInfo/batch")
    @ApiOperation("用户聚合信息-返回的代表需要刷新的")
    public ApiResult<List<SummeryInfoDTO>> getSummeryUserInfo(@Valid @RequestBody SummeryInfoReq req) {
        return ApiResult.success(userService.getSummeryUserInfo(req));
    }

    @PostMapping("/public/badges/batch")
    @ApiOperation("徽章聚合信息-返回的代表需要刷新的")
    public ApiResult<List<ItemInfoDTO>> getItemInfo(@Valid @RequestBody ItemInfoReq req) {
        return ApiResult.success(userService.getItemInfo(req));
    }

    @PutMapping("/name")
    @ApiOperation("修改用户名")
    public ApiResult<Void> modifyName(@Valid @RequestBody ModifyNameReq req) {
        userService.modifyName(RequestHolder.get().getUid(), req);
        return ApiResult.success();
    }

    @GetMapping("/badges")
    @ApiOperation("可选徽章预览")
    public ApiResult<List<BadgeResp>> badges() {
        return ApiResult.success(userService.badges(RequestHolder.get().getUid()));
    }

    @PutMapping("/badge")
    @ApiOperation("佩戴徽章")
    public ApiResult<Void> wearingBadge(@Valid @RequestBody WearingBadgeReq req) {
        userService.wearingBadge(RequestHolder.get().getUid(), req);
        return ApiResult.success();
    }

    @PutMapping("/black")
    @ApiOperation("拉黑用户")
    public ApiResult<Void> black(@Valid @RequestBody BlackReq req) {
        Long uid = RequestHolder.get().getUid();
        boolean hasPower = iRoleService.hasPower(uid, RoleEnum.ADMIN);
        AssertUtil.isTrue(hasPower, "没有权限");
        userService.black(req);
        return ApiResult.success();
    }

    @GetMapping("/public/search")
    @ApiOperation("搜索用户")
    public ApiResult<List<SummeryInfoDTO>> searchUser(@RequestParam String keyword) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(userService.searchUser(keyword, uid));
    }

    @PutMapping("/avatar")
    @ApiOperation("更新头像")
    public ApiResult<Void> updateAvatar(@RequestParam String avatar) {
        userService.updateAvatar(RequestHolder.get().getUid(), avatar);
        return ApiResult.success();
    }

    @PutMapping("/password")
    @ApiOperation("修改密码")
    public ApiResult<Void> modifyPassword(@Valid @RequestBody ModifyPasswordReq req) {
        userService.modifyPassword(RequestHolder.get().getUid(), req);
        return ApiResult.success();
    }

    @PostMapping("/email/code")
    @ApiOperation("发送绑定邮箱验证码")
    public ApiResult<Void> sendBindEmailCode(@Valid @RequestBody EmailCodeReq req) {
        emailAuthService.sendBindCode(RequestHolder.get().getUid(), req.getEmail());
        return ApiResult.success();
    }

    @PutMapping("/email")
    @ApiOperation("绑定邮箱")
    public ApiResult<Void> bindEmail(@Valid @RequestBody BindEmailReq req) {
        emailAuthService.bindEmail(RequestHolder.get().getUid(), req.getEmail(), req.getCode());
        return ApiResult.success();
    }
}


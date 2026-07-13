package com.ahao.haochat.common.user.controller;

import com.ahao.haochat.common.common.annotation.FrequencyControl;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.vo.request.auth.EmailCodeLoginReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.EmailCodeReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.EmailLoginReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.ForgotPasswordReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.LoginReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.LogoutReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.RefreshTokenReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.RegisterReq;
import com.ahao.haochat.common.user.domain.vo.response.auth.AuthTokenResp;
import com.ahao.haochat.common.user.service.EmailAuthService;
import com.ahao.haochat.common.user.service.LoginService;
import com.ahao.haochat.common.user.service.impl.AuthServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/capi/user/public")
@Slf4j
public class AuthController {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_SCHEMA = "Bearer ";

    @Autowired
    private AuthServiceImpl authService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private EmailAuthService emailAuthService;

    @PostMapping("/register/email/code")
    @FrequencyControl(time = 60, count = 3, target = FrequencyControl.Target.IP)
    public ApiResult<Void> sendRegisterEmailCode(@Valid @RequestBody EmailCodeReq req) {
        emailAuthService.sendRegisterCode(req.getEmail());
        return ApiResult.success();
    }

    @PostMapping("/register")
    @FrequencyControl(time = 60, count = 3, target = FrequencyControl.Target.IP)
    public ApiResult<Map<String, Object>> register(@Valid @RequestBody RegisterReq req) {
        User user = authService.register(req);
        Map<String, Object> result = new HashMap<>();
        result.put("uid", user.getId());
        result.put("username", user.getUsername());
        result.put("name", user.getName());
        return ApiResult.success(result);
    }

    @PostMapping("/login")
    @FrequencyControl(time = 60, count = 5, target = FrequencyControl.Target.IP)
    public ApiResult<AuthTokenResp> login(@Valid @RequestBody LoginReq req) {
        User user = authService.login(req);
        return ApiResult.success(loginService.loginWithTokens(user.getId()));
    }

    @PostMapping("/email/login")
    @FrequencyControl(time = 60, count = 5, target = FrequencyControl.Target.IP)
    public ApiResult<AuthTokenResp> emailLogin(@Valid @RequestBody EmailLoginReq req) {
        User user = emailAuthService.loginByEmail(req.getEmail(), req.getPassword());
        return ApiResult.success(loginService.loginWithTokens(user.getId()));
    }

    @PostMapping("/login/email/code")
    @FrequencyControl(time = 60, count = 3, target = FrequencyControl.Target.IP)
    public ApiResult<Void> sendLoginEmailCode(@Valid @RequestBody EmailCodeReq req) {
        emailAuthService.sendLoginCode(req.getEmail());
        return ApiResult.success();
    }

    @PostMapping("/email/code/login")
    @FrequencyControl(time = 60, count = 5, target = FrequencyControl.Target.IP)
    public ApiResult<AuthTokenResp> emailCodeLogin(@Valid @RequestBody EmailCodeLoginReq req) {
        User user = emailAuthService.loginByEmailCode(req.getEmail(), req.getCode());
        return ApiResult.success(loginService.loginWithTokens(user.getId()));
    }

    @PostMapping("/password/forgot/code")
    @FrequencyControl(time = 60, count = 3, target = FrequencyControl.Target.IP)
    public ApiResult<Void> forgotCode(@Valid @RequestBody EmailCodeReq req) {
        emailAuthService.sendForgotCode(req.getEmail());
        return ApiResult.success();
    }

    @PostMapping("/password/forgot/reset")
    @FrequencyControl(time = 60, count = 5, target = FrequencyControl.Target.IP)
    public ApiResult<Void> forgotReset(@Valid @RequestBody ForgotPasswordReq req) {
        emailAuthService.resetByCode(req.getEmail(), req.getCode(), req.getNewPassword());
        return ApiResult.success();
    }

    @PostMapping("/token/refresh")
    @FrequencyControl(time = 60, count = 20, target = FrequencyControl.Target.IP)
    public ApiResult<AuthTokenResp> refresh(@Valid @RequestBody RefreshTokenReq req) {
        return ApiResult.success(loginService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestBody(required = false) LogoutReq req, HttpServletRequest request) {
        loginService.logout(getBearerToken(request), req == null ? null : req.getRefreshToken());
        return ApiResult.success();
    }

    private String getBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(AUTHORIZATION_SCHEMA)) {
            return header.substring(AUTHORIZATION_SCHEMA.length());
        }
        return null;
    }
}

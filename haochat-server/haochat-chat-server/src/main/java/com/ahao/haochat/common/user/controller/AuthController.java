package com.ahao.haochat.common.user.controller;

import com.ahao.haochat.common.common.annotation.FrequencyControl;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.JwtUtils;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.vo.request.auth.LoginReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.RegisterReq;
import com.ahao.haochat.common.user.service.LoginService;
import com.ahao.haochat.common.user.service.impl.AuthServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 账号密码认证控制器
 */
@RestController
@RequestMapping("/capi/user/public")
@Slf4j
public class AuthController {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 注册 - 同一IP 1分钟最多3次
     */
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

    /**
     * 登录 - 同一IP 1分钟最多5次，同一UID 1分钟最多10次
     */
    @PostMapping("/login")
    @FrequencyControl(time = 60, count = 5, target = FrequencyControl.Target.IP)
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        User user = authService.login(req);
        String token = loginService.login(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("uid", user.getId());
        result.put("username", user.getUsername());
        result.put("name", user.getName());
        result.put("avatar", user.getAvatar());
        return ApiResult.success(result);
    }
}

package com.ahao.haochat.common.user.domain.vo.request.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求
 */
@Data
public class LoginReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}

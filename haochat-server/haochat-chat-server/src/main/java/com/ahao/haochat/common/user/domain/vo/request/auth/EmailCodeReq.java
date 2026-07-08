package com.ahao.haochat.common.user.domain.vo.request.auth;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/** 发送邮箱验证码（绑定 / 找回密码共用） */
@Data
public class EmailCodeReq {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式有误")
    @ApiModelProperty("邮箱")
    private String email;
}

package com.ahao.haochat.common.user.domain.vo.request.auth;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/** 找回密码：邮箱 + 验证码 + 新密码 */
@Data
public class ForgotPasswordReq {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式有误")
    @ApiModelProperty("邮箱")
    private String email;

    @NotBlank(message = "请输入验证码")
    @ApiModelProperty("验证码")
    private String code;

    @NotBlank(message = "请输入新密码")
    @ApiModelProperty("新密码")
    private String newPassword;
}

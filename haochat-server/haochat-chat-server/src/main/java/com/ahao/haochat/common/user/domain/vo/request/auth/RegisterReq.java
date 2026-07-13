package com.ahao.haochat.common.user.domain.vo.request.auth;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 注册请求
 */
@Data
public class RegisterReq {

    @NotBlank(message = "用户名不能为空")
    @Length(min = 3, max = 20, message = "用户名长度3-20个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Length(min = 1, max = 20, message = "昵称长度1-20个字符")
    private String name;

    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 50, message = "密码长度6-50个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$", message = "密码至少 8 位，且需同时包含大小写字母和数字")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$", message = "邮箱格式有误")
    private String email;

    @NotBlank(message = "请输入邮箱验证码")
    private String emailCode;
}
